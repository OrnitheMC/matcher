package matcher.gui.menu;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Alert.AlertType;

import matcher.Nester.NestingStatus;
import matcher.gui.Gui;
import matcher.type.ClassInstance;

public class NestingMenu extends Menu {
	private final Gui gui;

	NestingMenu(Gui gui) {
		super("Nesting");

		this.gui = gui;

		init();
	}

	private void init() {
		MenuItem menuItem = new MenuItem("Auto nest all");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> autoNestAll());

		for (int i = 1; i <= 10; i++) {
			int minScore = 10 * i;

			menuItem = new MenuItem("Auto nest all (" + minScore + "+)");
			getItems().add(menuItem);
			menuItem.setOnAction(event -> autoNestAll(minScore));
		}

		menuItem = new MenuItem("Auto nest class");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> autoNestClass());

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Re-evaluate all");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> filterAll());

		menuItem = new MenuItem("Status");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> showNestingStatus());
	}

	public void autoNestAll() {
		gui.runProgressTask(
				"Auto nesting...",
				p -> gui.getNester().autoNestAll(p),
				gui::onNestChange,
				Throwable::printStackTrace);
	}

	public void autoNestAll(int minScore) {
		gui.runProgressTask(
				"Auto nesting...",
				p -> gui.getNester().autoNestAll(minScore, p),
				gui::onNestChange,
				Throwable::printStackTrace);
	}

	public void autoNestClass() {
		gui.runProgressTask(
				"Auto nesting class...",
				p -> {
					ClassInstance clazz = gui.getSrcPane().getSelectedClass();

					if (clazz != null) {
						gui.getNester().autoNestClass(clazz.equiv);
					}
				},
				gui::onNestChange,
				Throwable::printStackTrace);
	}

	public void filterAll() {
		gui.runProgressTask("Re-evaluating potential nests...", 
				p -> gui.getNester().invalidatePotentialScores(),
				gui::onNestChange,
				Throwable::printStackTrace);
	}

	public void showNestingStatus() {
		NestingStatus status = gui.getNester().getStatus(true);

		gui.showAlert(AlertType.INFORMATION, "Nesting status", "Current nesting status",
				String.format("Nested classes: %d / %d (%.2f%%)%n   Anonymous classes: %d / %d (%.2f%%)%n   Inner classes: %d / %d (%.2f%%)",
						status.nestedClassCount, status.totalClassCount, (status.totalClassCount == 0 ? 0 : 100. * status.nestedClassCount / status.totalClassCount),
						status.anonymousClassCount, status.nestedClassCount, (status.nestedClassCount == 0 ? 0 : 100. * status.anonymousClassCount / status.nestedClassCount),
						status.innerClassCount, status.nestedClassCount, (status.nestedClassCount == 0 ? 0 : 100. * status.innerClassCount / status.nestedClassCount)
						));
	}
}
