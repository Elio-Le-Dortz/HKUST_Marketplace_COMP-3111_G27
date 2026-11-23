package com.hkust.marketplace;

import com.hkust.marketplace.utils.AutoReturnScheduler;
import com.hkust.marketplace.utils.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Main JavaFX application entry point for the HKUST Marketplace desktop client.
 *
 * <p>Initializes the primary window stage, launches the periodic {@link AutoReturnScheduler}
 * daemon for borrowed loan lifecycles, and loads the initial welcome portal.</p>
 */
public class MarketplaceApp extends Application {

    /**
     * Starts the JavaFX desktop application.
     *
     * @param primaryStage the primary application stage window
     */
    @Override
    public void start(Stage primaryStage) {
        AutoReturnScheduler.getInstance().start();
        SceneManager.init(primaryStage);
        SceneManager.showWelcome();
        primaryStage.setTitle("HKUST Marketplace");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        primaryStage.show();
    }

    /**
     * Cleanly stops background services and scheduled executors upon application exit.
     *
     * @throws Exception if superclass stop encounters an error
     */
    @Override
    public void stop() throws Exception {
        AutoReturnScheduler.getInstance().stop();
        super.stop();
    }

    /**
     * Standard Java application launcher method.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
