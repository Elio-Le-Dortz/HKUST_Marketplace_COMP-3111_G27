package com.hkust.marketplace.controllers;

import com.hkust.marketplace.utils.SceneManager;
import javafx.fxml.FXML;

/**
 * Controller for the initial welcome landing screen.
 *
 * <p>Allows users to select their desired portal entry point: Buyer, Seller, or Administrator.</p>
 */
public class WelcomeController {

    /**
     * Navigates to the Buyer Portal login screen.
     */
    @FXML
    private void goBuyer() {
        System.out.println("[Welcome] goBuyer()");
        SceneManager.showBuyerLogin();
    }

    /**
     * Navigates to the Seller Portal login screen.
     */
    @FXML
    private void goSeller() {
        System.out.println("[Welcome] goSeller()");
        SceneManager.showSellerLogin();
    }

    /**
     * Navigates to the Administrator Portal login screen.
     */
    @FXML
    private void goAdmin() {
        System.out.println("[Welcome] goAdmin()");
        SceneManager.showAdminLogin();
    }
}
