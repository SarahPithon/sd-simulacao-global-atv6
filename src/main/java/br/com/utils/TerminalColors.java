package br.com.utils;

public class TerminalColors {
    // Reset
    public static final String RESET = "\033[0m";
    
    // Regular Colors
    public static final String BLACK = "\033[0;30m";
    public static final String RED = "\033[0;31m";
    public static final String GREEN = "\033[0;32m";
    public static final String YELLOW = "\033[0;33m";
    public static final String BLUE = "\033[0;34m";
    public static final String PURPLE = "\033[0;35m";
    public static final String CYAN = "\033[0;36m";
    public static final String WHITE = "\033[0;37m";
    
    // Bold
    public static final String BLACK_BOLD = "\033[1;30m";
    public static final String RED_BOLD = "\033[1;31m";
    public static final String GREEN_BOLD = "\033[1;32m";
    public static final String YELLOW_BOLD = "\033[1;33m";
    public static final String BLUE_BOLD = "\033[1;34m";
    public static final String PURPLE_BOLD = "\033[1;35m";
    public static final String CYAN_BOLD = "\033[1;36m";
    public static final String WHITE_BOLD = "\033[1;37m";
    
    public static String multicastMessage(String message) {
        return CYAN + message + RESET;
    }
    
    public static String errorMessage(String message) {
        return RED + message + RESET;
    }
    
    public static String autoMessage(String message) {
        return GREEN + message + RESET;
    }
    
    public static String successMessage(String message) {
        return GREEN_BOLD + message + RESET;
    }
    
    public static String warningMessage(String message) {
        return YELLOW + message + RESET;
    }
}