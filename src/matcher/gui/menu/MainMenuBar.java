package matcher.gui.menu;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;

import matcher.config.Config;
import matcher.gui.Gui;
import matcher.gui.IGuiComponent;

public class MainMenuBar extends MenuBar implements IGuiComponent {
	public MainMenuBar(Gui gui) {
		this.gui = gui;

		init();
	}

	private void init() {
		fileMenu = addMenu(new FileMenu(gui));
		matchingMenu = addMenu(new MatchingMenu(gui));
		nestingMenu = addMenu(new NestingMenu(gui));
		mappingMenu = addMenu(new MappingMenu(gui));
		uidMenu = addMenu(new UidMenu(gui));
		viewMenu = addMenu(new ViewMenu(gui));
	}

	private <T extends Menu> T addMenu(T menu) {
		getMenus().add(menu);

		if (menu instanceof IGuiComponent) {
			gui.addListeningComponent((IGuiComponent) menu);
		}

		return menu;
	}

	@Override
	public void onProjectChange() {
		boolean isNesterProject = Config.getProjectConfig().isNesterProject();

		matchingMenu.setDisable(isNesterProject);
		nestingMenu.setDisable(!isNesterProject);
	}

	public FileMenu getFileMenu() {
		return fileMenu;
	}

	public MatchingMenu getMatchingMenu() {
		return matchingMenu;
	}

	public MappingMenu getMappingMenu() {
		return mappingMenu;
	}

	public UidMenu getUidMenu() {
		return uidMenu;
	}

	public ViewMenu getViewMenu() {
		return viewMenu;
	}

	private final Gui gui;

	private FileMenu fileMenu;
	private MatchingMenu matchingMenu;
	private NestingMenu nestingMenu;
	private MappingMenu mappingMenu;
	private UidMenu uidMenu;
	private ViewMenu viewMenu;
}
