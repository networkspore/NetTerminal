package io.netnotes.utils.strings;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.function.Function;

import io.netnotes.engine.noteBytes.processing.IntCounter;

/**
 * Enhanced input mask for formatted text entry.
 * Supports currency, decimals, phone numbers, dates, and custom formats.
 */
public class InputMask {
    private enum MaskType {
        CURRENCY,
        DECIMAL,
        PHONE,
        DATE,
        CUSTOM
    }
    
    private final MaskType m_type;
    private final String m_pattern;
    private DecimalFormat m_decimalFormat;
    private int m_maxLength = -1;
    private int m_decimalPlaces = -1;
    private Function<String, String> m_formatter;
    private Function<String, Integer> m_cursorTracker; // Maps old cursor pos to new cursor pos
    private Function<String, Boolean> m_validator;
    
    private InputMask(MaskType type, String pattern) {
        m_type = type;
        m_pattern = pattern;
    }
    
    // ========== Factory Methods ==========
    
    /**
     * Create a currency mask (e.g., $1,234.56)
     * 
     * @param maxDigitsBeforeDecimal Maximum digits before decimal point
     * @param decimalPlaces Number of decimal places (0 for no decimals)
     */
    public static InputMask currency(int maxDigitsBeforeDecimal, int decimalPlaces) {
        InputMask mask = new InputMask(MaskType.CURRENCY, "currency");
        
        StringBuilder pattern = new StringBuilder("$");
        pattern.append("#,##0");
        
        if (decimalPlaces > 0) {
            pattern.append(".");
            for (int i = 0; i < decimalPlaces; i++) {
                pattern.append("0");
            }
        }
        
        mask.m_decimalFormat = new DecimalFormat(pattern.toString());
        mask.m_maxLength = maxDigitsBeforeDecimal + (decimalPlaces > 0 ? decimalPlaces + 1 : 0);
        mask.m_decimalPlaces = decimalPlaces;
        
        // Currency cursor tracking: count significant chars (digits and decimal)
        mask.m_cursorTracker = (original) -> {
            return trackSignificantChars(original, c -> Character.isDigit(c) || c == '.');
        };
        
        return mask;
    }
    
    /**
     * Create a decimal mask (e.g., 1234.56)
     * 
     * @param maxDigitsBeforeDecimal Maximum digits before decimal point
     * @param decimalPlaces Number of decimal places (0 for no decimals)
     */
    public static InputMask decimal(int maxDigitsBeforeDecimal, int decimalPlaces) {
        InputMask mask = new InputMask(MaskType.DECIMAL, "decimal");
        
        StringBuilder pattern = new StringBuilder("#,##0");
        
        if (decimalPlaces > 0) {
            pattern.append(".");
            for (int i = 0; i < decimalPlaces; i++) {
                pattern.append("0");
            }
        }
        
        mask.m_decimalFormat = new DecimalFormat(pattern.toString());
        mask.m_maxLength = maxDigitsBeforeDecimal + (decimalPlaces > 0 ? decimalPlaces + 1 : 0);
        mask.m_decimalPlaces = decimalPlaces;
        
        // Decimal cursor tracking: count digits and decimal point
        mask.m_cursorTracker = (original) -> {
            return trackSignificantChars(original, c -> Character.isDigit(c) || c == '.');
        };
        
        return mask;
    }
    
    /**
     * Create a phone number mask (e.g., (123) 456-7890)
     * 
     * @param format Format pattern using '#' for digits
     *               Example: "(###) ###-####"
     */
    public static InputMask phone(String format) {
        InputMask mask = new InputMask(MaskType.PHONE, format);
        
        // Count how many digits the format expects
        IntCounter digitCounter = new IntCounter();
        for (char c : format.toCharArray()) {
            if(c == '#'){
                digitCounter.increment();
            } 
        }
        int digitCount = digitCounter.get();
        mask.m_maxLength = digitCount;
        
        // Phone formatter: insert formatting chars at fixed positions
        mask.m_formatter = (input) -> {
            // Extract just digits
            String digitsOnly = input.replaceAll("[^0-9]", "");
            
            if (digitsOnly.isEmpty()) {
                return "";
            }
            
            StringBuilder result = new StringBuilder();
            int digitIndex = 0;
            
            for (char c : format.toCharArray()) {
                if (c == '#') {
                    if (digitIndex < digitsOnly.length()) {
                        result.append(digitsOnly.charAt(digitIndex++));
                    } else {
                        break; // No more digits to insert
                    }
                } else {
                    // Only add formatting char if we have more digits coming
                    if (digitIndex < digitsOnly.length()) {
                        result.append(c);
                    }
                }
            }
            
            return result.toString();
        };
        
        // Phone cursor tracking: count digits only
        mask.m_cursorTracker = (original) -> {
            return trackSignificantChars(original, Character::isDigit);
        };
        
        // Phone validator: only allow digits
        mask.m_validator = (input) -> {
            String digitsOnly = input.replaceAll("[^0-9]", "");
            return digitsOnly.length() <= digitCount;
        };
        
        return mask;
    }
    
    /**
     * Create a date mask (e.g., MM/DD/YYYY)
     * 
     * @param format Format pattern using 'M', 'D', 'Y' for month, day, year
     *               Example: "MM/DD/YYYY"
     */
    public static InputMask date(String format) {
        InputMask mask = new InputMask(MaskType.DATE, format);
        
        // Count expected digits
        IntCounter digitCounter = new IntCounter();
        for (char c : format.toCharArray()) {
            if (c == 'M' || c == 'D' || c == 'Y') digitCounter.increment();
        }
        final int digitCount = digitCounter.get();
        mask.m_maxLength = digitCount;
        
        // Date formatter: insert separators at fixed positions
        mask.m_formatter = (input) -> {
            String digitsOnly = input.replaceAll("[^0-9]", "");
            
            if (digitsOnly.isEmpty()) {
                return "";
            }
            
            StringBuilder result = new StringBuilder();
            int digitIndex = 0;
            
            for (char c : format.toCharArray()) {
                if (c == 'M' || c == 'D' || c == 'Y') {
                    if (digitIndex < digitsOnly.length()) {
                        result.append(digitsOnly.charAt(digitIndex++));
                    } else {
                        break;
                    }
                } else {
                    // Add separator only if we have more digits
                    if (digitIndex < digitsOnly.length()) {
                        result.append(c);
                    }
                }
            }
            
            return result.toString();
        };
        
        // Date cursor tracking: count digits only
        mask.m_cursorTracker = (original) -> {
            return trackSignificantChars(original, Character::isDigit);
        };
        
        // Date validator: only allow digits
        mask.m_validator = (input) -> {
            String digitsOnly = input.replaceAll("[^0-9]", "");
            return digitsOnly.length() <= digitCount;
        };
        
        return mask;
    }
    
    /**
     * Create a custom mask with formatter and optional cursor tracker
     * 
     * @param formatter Function to format the input string
     * @param cursorTracker Function to map cursor position (optional, can be null)
     * @param maxLength Maximum input length
     */
    public static InputMask custom(Function<String, String> formatter, 
                                   Function<String, Integer> cursorTracker,
                                   int maxLength) {
        InputMask mask = new InputMask(MaskType.CUSTOM, "custom");
        mask.m_formatter = formatter;
        mask.m_cursorTracker = cursorTracker;
        mask.m_maxLength = maxLength;
        return mask;
    }
    
    /**
     * Simpler custom mask without cursor tracking
     */
    public static InputMask custom(Function<String, String> formatter, int maxLength) {
        return custom(formatter, null, maxLength);
    }
    
    // ========== Helper: Significant Character Tracking ==========
    
    /**
     * Track cursor position based on significant characters.
     * Used by cursor tracker functions to count meaningful chars.
     * 
     * @param original Original text before cursor
     * @param isSignificant Predicate to determine if char is significant
     * @return Count of significant characters
     */
    private static int trackSignificantChars(String original, Function<Character, Boolean> isSignificant) {
        int count = 0;
        for (int i = 0; i < original.length(); i++) {
            if (isSignificant.apply(original.charAt(i))) {
                count++;
            }
        }
        return count;
    }
    
    // ========== Formatting ==========
    
    /**
     * Format the input string according to the mask
     * 
     * @param input Raw input string
     * @return Formatted string
     */
    public String format(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        
        // Custom formatter
        if (m_formatter != null) {
            return m_formatter.apply(input);
        }
        
        // Decimal format (currency/decimal)
        if (m_decimalFormat != null) {
            String cleaned = FormattingHelpers.formatStringToNumber(input, 
                m_decimalPlaces >= 0 ? m_decimalPlaces : 10);
            
            if (cleaned.isEmpty() || FormattingHelpers.isTextZero(cleaned)) {
                return "";
            }
            
            try {
                BigDecimal value = new BigDecimal(cleaned);
                return m_decimalFormat.format(value);
            } catch (NumberFormatException e) {
                return input; // Return original if can't parse
            }
        }
        
        return input;
    }
    
    /**
     * Calculate new cursor position after formatting.
     * Returns the position where the cursor should be placed in the formatted string.
     * 
     * @param originalText Text before formatting
     * @param cursorPos Cursor position in original text
     * @param formattedText Text after formatting
     * @return New cursor position in formatted text
     */
    public int calculateCursorPosition(String originalText, int cursorPos, String formattedText) {
        if (originalText.isEmpty() || formattedText.isEmpty()) {
            return 0;
        }
        
        // Clamp cursor to valid range
        cursorPos = Math.max(0, Math.min(cursorPos, originalText.length()));
        
        // Use custom cursor tracker if provided
        if (m_cursorTracker != null) {
            String textBeforeCursor = originalText.substring(0, cursorPos);
            int significantCount = m_cursorTracker.apply(textBeforeCursor);
            
            // Find position in formatted text with same significant char count
            return findPositionBySignificantCount(formattedText, significantCount);
        }
        
        // Default: proportional mapping
        // If cursor was at 50% of original, place at 50% of formatted
        if (originalText.length() > 0) {
            double ratio = (double) cursorPos / originalText.length();
            return (int) Math.round(ratio * formattedText.length());
        }
        
        return formattedText.length();
    }
    
    /**
     * Find position in formatted text that has the specified count of significant characters
     */
    private int findPositionBySignificantCount(String formattedText, int targetCount) {
        Function<Character, Boolean> isSignificant = getSignificantCharPredicate();
        
        int count = 0;
        for (int i = 0; i < formattedText.length(); i++) {
            if (isSignificant.apply(formattedText.charAt(i))) {
                count++;
                if (count >= targetCount) {
                    return i + 1; // Position after this character
                }
            }
        }
        
        return formattedText.length(); // End of string
    }
    
    /**
     * Get predicate for what counts as a "significant" character for this mask type
     */
    private Function<Character, Boolean> getSignificantCharPredicate() {
        switch (m_type) {
            case CURRENCY:
            case DECIMAL:
                return c -> Character.isDigit(c) || c == '.';
            case PHONE:
            case DATE:
                return Character::isDigit;
            case CUSTOM:
                // For custom, default to counting all chars
                return _ -> true;
            default:
                return _ -> true;
        }
    }
    
    // ========== Validation ==========
    
    /**
     * Check if input is valid for this mask
     */
    public boolean isValidInput(String input) {
        if (input == null) {
            return false;
        }
        
        // Custom validator
        if (m_validator != null) {
            return m_validator.apply(input);
        }
        
        // Length check
        if (m_maxLength > 0 && input.length() > m_maxLength) {
            return false;
        }
        
        // Decimal format validation
        if (m_decimalFormat != null) {
            // Allow digits, decimal point, and handle partial input
            return input.matches("[\\d.,]*");
        }
        
        return true;
    }
    
    // ========== Getters ==========
    
    public String getPattern() {
        return m_pattern;
    }
    
    public MaskType getType() {
        return m_type;
    }
    
    public int getMaxLength() {
        return m_maxLength;
    }
}


// ========== Example Usage in Application ==========

/*
// Currency field
BufferedTextField currencyField = new BufferedTextField();
currencyField.setInputMask(InputMask.currency(10, 2)); // $1,234.56
currencyField.setPlaceholderText("$0.00");

// Phone number field
BufferedTextField phoneField = new BufferedTextField();
phoneField.setInputMask(InputMask.phone("(###) ###-####")); // (123) 456-7890
phoneField.setPlaceholderText("(000) 000-0000");

// Date field
BufferedTextField dateField = new BufferedTextField();
dateField.setInputMask(InputMask.date("MM/DD/YYYY")); // 12/31/2024
dateField.setPlaceholderText("MM/DD/YYYY");

// Decimal field
BufferedTextField decimalField = new BufferedTextField();
decimalField.setInputMask(InputMask.decimal(8, 4)); // 1,234.5678
decimalField.setPlaceholderText("0.0000");

// Custom mask - uppercase text
BufferedTextField upperField = new BufferedTextField();
upperField.setInputMask(InputMask.custom(
    String::toUpperCase,
    original -> original.length(), // Track all characters
    50
));

// Custom mask - credit card (XXXX-XXXX-XXXX-XXXX)
BufferedTextField ccField = new BufferedTextField();
ccField.setInputMask(InputMask.custom(
    input -> {
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return "";
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < digits.length() && i < 16; i++) {
            if (i > 0 && i % 4 == 0) result.append("-");
            result.append(digits.charAt(i));
        }
        return result.toString();
    },
    original -> original.replaceAll("[^0-9]", "").length(), // Count digits
    16
));
*/