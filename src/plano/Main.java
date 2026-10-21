package plano;

import plano.repository.DataLoadException;
import plano.repository.DataStore;
import plano.ui.MenuController;
import plano.util.InputClosedException;

/**
 * Main application entry point for PlanO Event Management System.
 * Final Project for Object-Oriented Programming (OOPROG) - Section C2B
 * Group 6:
 *  - Axellerosh F. Lubi
 *  - Courtney Judge P. Fabello
 *  - Gavin Hadrian M. Lodor
 */
public class Main {
    public static void main(String[] args) {
        // Initialize persistent storage (seeds demo data on the first run)
        DataStore dataStore;
        try {
            dataStore = new DataStore("data");
        } catch (DataLoadException e) {
            System.out.println("[ERROR] " + e.getMessage());
            System.exit(1);
            return;
        }

        // Start console user interface controller
        try {
            new MenuController(dataStore).start();
        } catch (InputClosedException e) {
            // Every change is saved as it happens, so there is nothing left to save here.
            System.out.println();
            System.out.println("[INFO] Input ended. Exiting PlanO.");
        }
    }
}
