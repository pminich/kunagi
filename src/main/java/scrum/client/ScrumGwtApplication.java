package scrum.client;

import ilarkesto.gwt.client.GwtLogger;
import ilarkesto.scope.NonConcurrentScopeManager;
import scrum.client.collaboration.Subject;
import scrum.client.files.File;
import scrum.client.impediments.Impediment;
import scrum.client.issues.Issue;
import scrum.client.project.Quality;
import scrum.client.project.Requirement;
import scrum.client.risks.Risk;
import scrum.client.sprint.Task;
import scrum.client.workspace.WorkspaceWidget;

import com.google.gwt.user.client.ui.RootPanel;

public class ScrumGwtApplication extends GScrumGwtApplication {

	public static final String[] REFERENCE_PREFIXES = new String[] { Requirement.REFERENCE_PREFIX,
			Task.REFERENCE_PREFIX, Quality.REFERENCE_PREFIX, Issue.REFERENCE_PREFIX, Impediment.REFERENCE_PREFIX,
			Risk.REFERENCE_PREFIX, File.REFERENCE_PREFIX, Subject.REFERENCE_PREFIX };

	private final GwtLogger log = GwtLogger.createLogger(getClass());

	private ComponentManager cm;
	private ApplicationInfo applicationInfo;

	public void onModuleLoad() {
		System.out.println("ScrumGwtApplication.onModuleLoad()");

		cm = new ComponentManager();

		NonConcurrentScopeManager scopeManager = new NonConcurrentScopeManager("app");

		final WorkspaceWidget workspace = cm.getUi().getWorkspace();
		workspace.lock("Loading...");

		RootPanel rootPanel = RootPanel.get();
		rootPanel.getElement().getStyle().setProperty("position", "relative");
		rootPanel.add(workspace);
		callStartSession(new Runnable() {

			public void run() {
				cm.getPublicContext().activate();
				cm.getPinger().reschedule();
			}
		});

		cm.getWiki();
		ScrumJs.initialize();
	}

	@Override
	protected void onServerData(DataTransferObject data) {
		if (data.usersStatus != null) cm.getUsersStatus();

		if (data.applicationInfo != null) {
			this.applicationInfo = data.applicationInfo;
			log.debug("applicationInfo:", data.applicationInfo);
		} else {
			assert this.applicationInfo != null;
		}

		cm.getEventBus().fireServerDataReceived(data);
	}

	public ApplicationInfo getApplicationInfo() {
		return applicationInfo;
	}

	@Override
	protected void handleCommunicationError(Throwable ex) {
		GwtLogger.ERROR("Communication Error:", ex);
		cm.getUi().getWorkspace().abort("Lost connection to server.");
	}

	public final void callStartSession(Runnable callback) {
		getScrumService().startSession(new DefaultCallback<DataTransferObject>(callback));
	}

}
