/*
 * Copyright 2011 Witoslaw Koczewsi <wi@koczewski.de>, Artjom Kochtchi
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero
 * General Public License as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package scrum.server.sprint;

import ilarkesto.base.Str;
import ilarkesto.base.Utl;
import ilarkesto.base.time.Date;
import ilarkesto.core.logging.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import scrum.server.admin.User;
import scrum.server.common.Numbered;
import scrum.server.issues.Issue;
import scrum.server.journal.ChangeDao;
import scrum.server.project.Project;
import scrum.server.project.Requirement;
import scrum.server.release.Release;

public class Sprint extends GSprint implements Numbered {

	private static final Log log = Log.get(Sprint.class);

	// --- dependencies ---

	private static transient ChangeDao changeDao;

	public static void setChangeDao(ChangeDao changeDao) {
		Sprint.changeDao = changeDao;
	}

	// --- ---

	public Release getNextRelease() {
		List<Release> releases = new ArrayList<Release>(getReleases());
		Collections.sort(releases, Release.DATE_REVERSE_COMPARATOR);
		return releases.isEmpty() ? null : Utl.getElement(releases, 0);
	}

	public void pullRequirement(Requirement requirement, User user) {
		requirement.setSprint(this);
		for (Task task : requirement.getTasksInSprint()) {
			task.reset();
		}
		moveToBottom(requirement);
		getDaySnapshot(Date.today()).updateWithCurrentSprint();

		changeDao.postChange(requirement, user, "sprintId", null, getId());
	}

	public void kickRequirement(Requirement requirement, User user) {
		int burned = 0;
		for (Task task : requirement.getTasksInSprint()) {
			burned += task.getBurnedWork();
			task.reset();
		}

		requirement.setClosed(false);
		requirement.setSprint(null);
		requirement.setDirty(burned > 0);
		requirement.getProject().moveRequirementToTop(requirement);
		SprintDaySnapshot daySnapshot = getDaySnapshot(Date.today());
		daySnapshot.addBurnedWorkFromDeleted(burned);
		daySnapshot.updateWithCurrentSprint();

		changeDao.postChange(requirement, user, "sprintId", getId(), null);
	}

	public void moveToBottom(Requirement requirement) {
		List<String> orderIds = getRequirementsOrderIds();
		orderIds.remove(requirement.getId());
		orderIds.add(requirement.getId());
		setRequirementsOrderIds(orderIds);
	}

	public void close() {
		SprintReport report = sprintReportDao.postSprintReport(this);
		report.setRequirementsOrderIds(getRequirementsOrderIds());
		report.setBurnedWork(getBurnedWork());

		float velocity = 0;
		StringBuilder releaseNotes = new StringBuilder();
		releaseNotes.append("'''Stories from ").append(getReferenceAndLabel()).append("'''\n\n");
		Collection<Requirement> completedRequirements = new ArrayList<Requirement>();
		Collection<Requirement> incompletedRequirements = new ArrayList<Requirement>();
		List<Requirement> requirements = new ArrayList<Requirement>(getRequirements());
		Collections.sort(requirements, getRequirementsOrderComparator());
		for (Requirement requirement : requirements) {
			releaseNotes.append("* " + (requirement.isClosed() ? "" : "(UNFINISHED) "))
					.append(requirement.getReferenceAndLabel()).append("\n");
			for (Task task : requirement.getTasksInSprint()) {
				if (task.isClosed()) {
					report.addClosedTask(task);
				} else {
					report.addOpenTask(task);
				}
			}
			if (requirement.isClosed()) {
				completedRequirements.add(requirement);
				changeDao.postChange(requirement, null, "completedInSprint", null, getReference());
			} else {
				getProject().addRequirementsOrderId(incompletedRequirements.size(), requirement.getId());
				incompletedRequirements.add(requirement);
				changeDao.postChange(requirement, null, "rejectedInSprint", null, getReference());
			}
		}

		report.setCompletedRequirements(completedRequirements);
		report.setRejectedRequirements(incompletedRequirements);

		setCompletedRequirementsData(SprintHistoryHelper.encodeRequirementsAndTasks(completedRequirements));
		setIncompletedRequirementsData(SprintHistoryHelper.encodeRequirementsAndTasks(incompletedRequirements));

		for (Requirement requirement : requirements) {
			List<Task> tasks = new ArrayList<Task>(requirement.getTasksInSprint());
			if (requirement.isClosed()) {
				Float work = requirement.getEstimatedWork();
				if (work != null) velocity += work;
			} else {
				for (Task task : tasks) {
					if (task.isClosed()) {
						task.setClosedInPastSprint(this);
					} else {
						task.reset();
					}
				}
			}
			requirement.setSprint(null);
		}

		Set<Issue> fixedIssues = getFixedIssues();
		if (!fixedIssues.isEmpty()) {
			releaseNotes.append("'''\nFixed bugs'''\n\n");
			for (Issue issue : fixedIssues) {
				releaseNotes.append("* ").append(issue.getReferenceAndLabel()).append("\n");
			}
		}

		for (Release release : getReleases()) {
			StringBuilder sb = new StringBuilder();
			if (release.isReleaseNotesSet()) {
				sb.append(release.getReleaseNotes());
				sb.append("\n\n");
			}
			sb.append(releaseNotes.toString());
			release.setReleaseNotes(sb.toString());
		}
		setVelocity(velocity);
		Project project = getProject();
		setProductOwners(project.getProductOwners());
		setScrumMasters(project.getScrumMasters());
		setTeamMembers(project.getTeamMembers());
		int roundedVelocity = Math.round(velocity);
		if (roundedVelocity > 0) project.setVelocity(roundedVelocity);
	}

	public Set<Issue> getFixedIssues() {
		Set<Issue> fixedIssues = new HashSet<Issue>();
		for (Release release : getReleases()) {
			for (Issue issue : release.getFixIssues()) {
				if (issue.isFixed()) fixedIssues.add(issue);
			}
		}
		return fixedIssues;
	}

	public String getProductOwnersAsString() {
		return Str.concat(User.getNames(getProductOwners()), ", ");
	}

	public String getScrumMastersAsString() {
		return Str.concat(User.getNames(getScrumMasters()), ", ");
	}

	public String getTeamMembersAsString() {
		return Str.concat(User.getNames(getTeamMembers()), ", ");
	}

	public List<SprintDaySnapshot> getDaySnapshots() {
		return sprintDaySnapshotDao.getSprintDaySnapshots(this);
	}

	public Set<SprintDaySnapshot> getExistingDaySnapshots() {
		return sprintDaySnapshotDao.getSprintDaySnapshotsBySprint(this);
	}

	public Integer getLengthInDays() {
		if (!isBeginSet() || !isEndSet()) return null;
		return getBegin().getPeriodTo(getEnd()).toDays();
	}

	public SprintDaySnapshot getDaySnapshot(Date date) {
		return sprintDaySnapshotDao.getSprintDaySnapshot(this, date, true);
	}

	public int getRemainingWork() {
		int sum = 0;
		for (Task task : getTasks()) {
			Integer effort = task.getRemainingWork();
			if (effort != null) sum += effort;
		}
		return sum;
	}

	public int getBurnedWork() {
		int sum = 0;
		for (Task task : getTasks()) {
			sum += task.getBurnedWork();
		}
		return sum;
	}

	public Set<Task> getTasks() {
		return taskDao.getTasksBySprint(this);
	}

	public String getReference() {
		return scrum.client.sprint.Sprint.REFERENCE_PREFIX + getNumber();
	}

	@Override
	public void updateNumber() {
		if (getNumber() == 0) setNumber(getProject().generateSprintNumber());
	}

	@Override
	public void ensureIntegrity() {
		super.ensureIntegrity();
		updateNumber();

		if (getProject().isCurrentSprint(this)) {
			if (!isBeginSet()) setBegin(Date.today());
			if (!isEndSet()) setEnd(getBegin().addDays(14));

			// auto stretch sprint
			if (getEnd().isYesterday()) setEnd(Date.today());
		}

		if (isBeginSet() && isEndSet() && getBegin().isAfter(getEnd())) setEnd(getBegin());

		// delete when not current and end date older than 4 weeks
		// if (isEndSet() && !getProject().isCurrentSprint(this) && getEnd().isPast()
		// && getEnd().getPeriodToNow().toWeeks() > 4) {
		// LOG.info("Deleting sprint, which ended on", getEnd(), "->", toString());
		// getDao().deleteEntity(this);
		// }

	}

	@Override
	public boolean isVisibleFor(User user) {
		return getProject().isVisibleFor(user);
	}

	public String getReferenceAndLabel() {
		return getReference() + " " + getLabel();
	}

	@Override
	public String toString() {
		return getReferenceAndLabel();
	}

	public void burndownTasksRandomly(Date begin, Date end) {
		int days = getBegin().getPeriodTo(getEnd()).toDays();
		days -= (days / 7) * 2;
		int defaultWorkPerDay = getRemainingWork() / days;

		getDaySnapshot(begin).updateWithCurrentSprint();
		begin = begin.nextDay();
		while (begin.isBefore(end)) {
			if (!begin.getWeekday().isWeekend()) {
				int toBurn = Utl.randomInt(0, defaultWorkPerDay + (defaultWorkPerDay * 2));
				int totalRemaining = getRemainingWork();
				for (Task task : getTasks()) {
					if (toBurn == 0) break;
					int remaining = task.getRemainingWork();
					int burn = Math.min(toBurn, remaining);
					remaining -= burn;
					toBurn -= burn;
					task.setBurnedWork(task.getBurnedWork() + burn);
					if (Utl.randomInt(1, 10) == 1) {
						remaining += Utl.randomInt(-defaultWorkPerDay * 2, defaultWorkPerDay * 3);
					}
					if (totalRemaining == 0) {
						remaining += Utl.randomInt(defaultWorkPerDay * 3, defaultWorkPerDay * 5);
						totalRemaining = remaining;
					}
					task.setRemainingWork(remaining);
				}
			}
			getDaySnapshot(begin).updateWithCurrentSprint();
			begin = begin.nextDay();
		}
	}

	private transient Comparator<Requirement> requirementsOrderComparator;

	public Comparator<Requirement> getRequirementsOrderComparator() {
		if (requirementsOrderComparator == null) requirementsOrderComparator = new Comparator<Requirement>() {

			@Override
			public int compare(Requirement a, Requirement b) {
				List<String> order = getRequirementsOrderIds();
				int additional = order.size();
				int ia = order.indexOf(a.getId());
				if (ia < 0) {
					ia = additional;
					additional++;
				}
				int ib = order.indexOf(b.getId());
				if (ib < 0) {
					ib = additional;
					additional++;
				}
				return ia - ib;
			}
		};
		return requirementsOrderComparator;
	}

}
