package com.hkust.marketplace;

/**
 * Entry point to use when starting the application from an IDE such as IntelliJ IDEA.
 *
 * <p>The JDK refuses to start a main class that extends
 * {@link javafx.application.Application} when the JavaFX jars are on the class path
 * rather than the module path, and fails with "JavaFX runtime components are missing".
 * This class does not extend {@code Application}, so running it directly works with
 * the class path that the IDE builds from {@code pom.xml}.
 * ({@code mvnw javafx:run} does not need it.)</p>
 */
public final class Launcher {

    private Launcher() {
        // Entry point only: not meant to be instantiated.
    }

    /**
     * Starts the HKUST Marketplace application.
     *
     * @param args command-line arguments, passed on to {@link MarketplaceApp#main(String[])}
     */
    public static void main(String[] args) {
        MarketplaceApp.main(args);
    }
}
