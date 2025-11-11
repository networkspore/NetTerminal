package io.netnotes.utils.strings;

public class FormattingHelpers {
    
    


    public static String formatStringLineLength(String str, int len){
        return str.replaceAll("(.{"+len+"})", "$1\n");
    }



    public static boolean onlyZero(String str) {
        
        for (int i = 0 ; i < str.length() ; i++){
            String c = str.substring(i, i+1);
            if(!(c.equals("0") || c.equals("."))){
                return false;
            }
        }
        return true;
    }

    public static boolean isTextZero(String str){
        str = str.strip();
        
        if(str.length() == 0){
            return true;
        }

        int index = str.indexOf(".");

        String leftSide = index != -1 ? str.substring(0, index) : str;
        
        String rightSide = index != -1 ? str.substring(index + 1) : "";
        
        for (int i = 0 ; i < leftSide.length() ; i++){
            String c = leftSide.substring(i, i+1);
            if(!c.equals("0")){
                return false;
            }
        }

        for (int i = 0 ; i < rightSide.length() ; i++){
            String c = rightSide.substring(i, i+1);
            if(!c.equals("0")){
                return false;
            }
        }
        
        return true;
    }



    public static String formatStringToNumber(String number){
        number = number.replaceAll("[^0-9.]", "");
        int index = number.indexOf(".");
        String leftSide = index != -1 ? number.substring(0, index + 1) : number;
        leftSide = leftSide.equals(".") ? "0." : leftSide;
        String rightSide = index != -1 && index != number.length() - 1 ?  number.substring(index + 1) : "";
        rightSide = rightSide.length() > 0 ? rightSide.replaceAll("[^0-9]", "") : "";

        number = leftSide + rightSide;
        return number;
    
    }

    public static String formatStringToNumber(String number, int decimals){
        number = number.replaceAll("[^0-9.]", "");
        int index = number.indexOf(".");
        String leftSide = index != -1 ? number.substring(0, index + 1) : number;
        leftSide = leftSide.equals(".") ? "0." : leftSide;
        String rightSide = index != -1 && index != number.length() - 1 ?  number.substring(index + 1) : "";
        rightSide = rightSide.length() > 0 ? rightSide.replaceAll("[^0-9]", "") : "";
        rightSide = rightSide.length() > decimals ? rightSide.substring(0, decimals) : rightSide;

        number = leftSide + rightSide;
        return number;

    }

    public static String removeNonAlphaNumberic(String str)
    {
        return str.replaceAll("[^a-zA-Z0-9\\.\\-]", "");
    }

    public static String formatedBytes(long bytes, int decimals) {

        if (bytes == 0) {
            return "0 Bytes";
        }

        double k = 1024;
        int dm = decimals < 0 ? 0 : decimals;

        String[] sizes = new String[]{"Bytes", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"};

        int i = (int) Math.floor(Math.log((double) bytes) / Math.log(k));

        return String.format("%." + dm + "f", bytes / Math.pow(k, i)) + " " + sizes[i];

    }

}
