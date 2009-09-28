package scrum.client.admin;

import ilarkesto.gwt.client.AAction;
import ilarkesto.gwt.client.AViewEditWidget;
import ilarkesto.gwt.client.ButtonWidget;
import ilarkesto.gwt.client.ToolbarWidget;
import scrum.client.ScrumGwtApplication;
import scrum.client.common.FieldsWidget;
import scrum.client.workspace.Ui;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

public class PasswordChangeWidget extends AViewEditWidget {

	private FieldsWidget fields;
	private ButtonWidget confirmButton;
	private TextBox currentPasswordBox;
	private TextBox newPasswordBox;
	private TextBox newPasswordRepeatBox;

	@Override
	protected Widget onViewerInitialization() {
		return new Label("*****");
	}

	@Override
	protected void onViewerUpdate() {}

	@Override
	protected Widget onEditorInitialization() {
		currentPasswordBox = new PasswordTextBox();
		newPasswordBox = new PasswordTextBox();
		newPasswordRepeatBox = new PasswordTextBox();

		fields = new FieldsWidget();
		fields.add("Current Password", currentPasswordBox);
		fields.add("New Password", newPasswordBox);
		fields.add("Repeat new Password", newPasswordRepeatBox);

		FlowPanel mainpanel = new FlowPanel();
		mainpanel.setWidth("100%");
		mainpanel.add(fields);
		confirmButton = new ButtonWidget(new SaveChangesAction());
		ToolbarWidget toolbar = new ToolbarWidget(true);
		toolbar.add(confirmButton);
		mainpanel.add(toolbar);

		return mainpanel;
	}

	@Override
	protected void onEditorUpdate() {
		currentPasswordBox.setText("");
		newPasswordBox.setText("");
		newPasswordRepeatBox.setText("");
		fields.update();
	}

	@Override
	protected void onEditorSubmit() {
		String currentPassword = currentPasswordBox.getText();
		String newPassword = newPasswordBox.getText();
		String newPasswordRepeat = newPasswordRepeatBox.getText();
		if (currentPassword == null) {
			Ui.get().showError("old password is empty");
			return;
		} else if (newPassword == null) {
			Ui.get().showError("new password is empty");
			return;
		} else if (newPasswordRepeat == null) {
			Ui.get().showError("new password repeat is empty");
			return;
		} else if (!newPassword.equals(newPasswordRepeat)) {
			Ui.get().showError("the new password repeat did not match the new password.");
			return;
		}
		ScrumGwtApplication.get().callChangePassword(currentPassword, newPassword);
	}

	class SaveChangesAction extends AAction {

		@Override
		public String getLabel() {
			return "Change Password";
		}

		@Override
		protected void onExecute() {
			submitEditor();
		}

	}

}