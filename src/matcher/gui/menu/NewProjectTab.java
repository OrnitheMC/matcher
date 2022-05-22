package matcher.gui.menu;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.stage.Window;

import matcher.config.ProjectConfig;

public class NewProjectTab extends Tab {
	private final NewProjectPane content;

	public NewProjectTab(ProjectConfig config, Window window, Node okButton, boolean forNester) {
		content = new NewProjectPane(config, window, okButton, forNester);

		setText(forNester ? "Nester" : "Matcher");
		setContent(content);
		selectedProperty().addListener((ov, wasSelected, isSelected) -> content.setSelected(isSelected));
	}

	public ProjectConfig createConfig() {
		return content.createConfig();
	}
}
