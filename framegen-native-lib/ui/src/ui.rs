use adw::{self, subclass::prelude::ObjectSubclassIsExt};
use gtk::prelude::{WidgetExt, EditableExt, GtkWindowExt, SwitchExt};

use crate::config;
use crate::wrapper;

pub mod entry_handler;
pub mod main_handler;
pub mod sidebar_handler;

pub fn build(app: &adw::Application) {
    // create the main window
    let window = wrapper::Window::new(app);
    window.set_application(Some(app));
    let imp = window.imp();

    // load profiles from configuration
    let config = config::get_config().unwrap();
    for game in config.game.iter() {
        let entry = wrapper::entry::Entry::new();
        entry.set_exe(game.exe.clone());
        entry_handler::add_entry(entry, imp.sidebar.imp().profiles.clone());
    }

    if let Some(dll_path) = config.global.dll {
        imp.main.imp().dll.imp().entry.set_text(&dll_path);
    }

    if let Some(ncnn_model_path) = config.global.ncnn_model {
        imp.main.imp().ncnn_model.imp().entry.set_text(&ncnn_model_path);
    }
    imp.main.imp().use_ncnn.imp().switch.set_state(config.global.use_ncnn);

    // register handlers on sidebar pane.
    sidebar_handler::register_signals(&imp.sidebar, imp.main.clone());

    // register handlers on main pane.
    main_handler::register_signals(imp.sidebar.clone(), &imp.main);

    // activate the first profile if available
    if let Some(entry) = imp.sidebar.imp().profiles.row_at_index(0) {
        entry.activate();
    }

    // present the window
    window.present();
}
