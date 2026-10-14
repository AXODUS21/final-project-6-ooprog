package plano;

import plano.repository.DataStore;
import plano.ui.MenuController;

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
        // Initialize persistent storage and seed data
        DataStore dataStore = new DataStore("data");

        // Start console user interface controller
        MenuController controller = new MenuController(dataStore);
        controller.start();
    }
}
