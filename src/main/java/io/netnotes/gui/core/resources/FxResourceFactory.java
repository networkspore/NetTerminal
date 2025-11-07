package io.netnotes.gui.fx.display;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import io.netnotes.engine.utils.LoggingHelpers;
import io.netnotes.gui.fx.display.javafxsvg.SvgImageLoaderFactory;

import io.netnotes.engine.utils.Version;
import io.netnotes.engine.utils.github.GitHubInfo;
import io.netnotes.engine.utils.PomProperties;

import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;

public class FxResourceFactory {
    public static final KeyCombination KEY_COMB_CTL_Z = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    public static final KeyCombination KEY_COMB_CTL_U = new KeyCodeCombination(KeyCode.U, KeyCombination.SHORTCUT_DOWN);

    public static int CURSOR_DELAY = 600;

    public final static Font openSansTxt;
    public final static Font openSansSmall;
    public final static Font HeadingFont;
    public final static Font txtFont;
    public final static Font titleFont;
    public final static Font smallFont;

    public final static File LOG_FILE = new File("netnotes-log.txt");
    public final static File STREAM_LOG_FILE = new File("netnotes-stream-log.txt");

    public final static String APP_NAME =  "Netnotes";

    public static final GitHubInfo GITHUB_PROJECT_INFO = new GitHubInfo("networkspore", "Netnotes-Widow");

    public static final String POM_GROUP_ID = "io.netnotes";
    public static final String POM_ARTIFACT_ID = "loader";
    public static final Version POM_VERSION;

    public final static String ASSETS_DIRECTORY = "/assets";
    public final static String FONTS_DIRECTORY = "/fonts";
    public final static String CSS_DIRECTORY = "/css";

    public static final String WAITING_IMG = ASSETS_DIRECTORY + "/spinning.gif";
    public static final String OPEN_IMG = ASSETS_DIRECTORY + "/open-outline-white-20.png";
    public static final String DISK_IMG = ASSETS_DIRECTORY + "/save-outline-white-20.png";
    public static final String ADD_IMG = ASSETS_DIRECTORY + "/add-outline-white-40.png";

    public static final String PRIMARY_FONT = FONTS_DIRECTORY + "/OCRAEXT.TTF";
    public static final String PRIMARY_FONT_FAMILY = "OCR A Extended";

    public static final String EMOJI_FONT = FONTS_DIRECTORY + "/OpenSansEmoji.ttf";
    public static final String EMOJI_FONT_FAMILY = "OpenSansEmoji, Regular";

    public final static String DEFAULT_CSS = CSS_DIRECTORY + "/widow.css";

    public final static String WIDOW120 = ASSETS_DIRECTORY + "/widow-120.png";
    public final static String WIDOW256 = ASSETS_DIRECTORY + "/widow-256.png";

    static{
        SvgImageLoaderFactory.install();
        initAwtFonts();
        Font.loadFont(FxResourceFactory.class.getResource(PRIMARY_FONT).toExternalForm(),16);
        Font.loadFont(FxResourceFactory.class.getResource(EMOJI_FONT).toExternalForm(),20);

        PomProperties pomReader = PomProperties.create(POM_GROUP_ID, POM_ARTIFACT_ID);

        POM_VERSION = pomReader.getVersion();

        openSansTxt = Font.font(EMOJI_FONT_FAMILY, FontWeight.BOLD, 16);
        openSansSmall = Font.font(EMOJI_FONT_FAMILY , FontWeight.BOLD, 12);
        HeadingFont =  Font.font(PRIMARY_FONT_FAMILY, FontWeight.BOLD, 20);
        txtFont = Font.font(PRIMARY_FONT_FAMILY, 18);
        titleFont = Font.font(PRIMARY_FONT_FAMILY, FontWeight.BOLD, 16);
        smallFont = Font.font(PRIMARY_FONT_FAMILY, 14);
    }

    public static void initAwtFonts(){
        try( 
            InputStream stream = FxResourceFactory.class.getResource(PRIMARY_FONT).openStream(); 
        ) {
            java.awt.Font ocrFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream).deriveFont(48f);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(ocrFont);
        } catch (FontFormatException | IOException e) {
            LoggingHelpers.writeLogMsg(LOG_FILE, "Error registering font:", e);
        }

        try( 
            InputStream stream = FxResourceFactory.class.getResource(EMOJI_FONT).openStream(); 
        ) {
            java.awt.Font ocrFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream).deriveFont(48f);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(ocrFont);
        } catch (FontFormatException | IOException e) {
            LoggingHelpers.writeLogMsg(LOG_FILE, "Error registering font:", e);
        }

    }
    
  
    public static Color txtColor = Color.web("#cdd4da");
    public static Color altColor = Color.web("#777777");
    public static Color formFieldColor = new Color(.8, .8, .8, .9);
    

    public static final String APP_LOGO_256 = ASSETS_DIRECTORY + "/logo-256.png";
    public static final String APP_ICON_15 = ASSETS_DIRECTORY + "/icon15.png";

    public static final String UNKNOWN_IMAGE_PATH = ASSETS_DIRECTORY + "/unknown-unit.png";

    public final static ExtensionFilter JAR_EXT = new FileChooser.ExtensionFilter("application/x-java-archive", "*.jar");
    public final static ExtensionFilter JSON_EXT = new FileChooser.ExtensionFilter("application/json", "*.json");




    public static final String APP_ICON = ASSETS_DIRECTORY + "/apps-outline-35.png";
    public static final String CHECKMARK_ICON = ASSETS_DIRECTORY + "/checkmark-25.png";
    public static final String MINIMIZE_ICON = ASSETS_DIRECTORY + "/minimize-white-20.png";
    public static final String MAXIMIZE_ICON = ASSETS_DIRECTORY + "/maximize-white-30.png";
    public static final String FILL_RIGHT_ICON = ASSETS_DIRECTORY + "/fillRight.png";
    public static final String CLOSE_ICON = ASSETS_DIRECTORY + "/close-outline-white.png";
    public static final String SETTINGS_ICON = ASSETS_DIRECTORY + "/settings-outline-white-30.png";
    public static final String SETTINGS_ICON_120 = ASSETS_DIRECTORY + "/settings-outline-white-120.png";
    public static final String CARET_DOWN_ICON = ASSETS_DIRECTORY + "/caret-down-15.png";
    public static final String MENU_ICON = ASSETS_DIRECTORY + "/menu-outline-30.png";
    public static final String NAV_ICON = ASSETS_DIRECTORY + "/navigate-outline-white-30.png";
    public static final String SHOW_ICON = ASSETS_DIRECTORY + "/eye-30.png";
    public static final String HIDE_ICON = ASSETS_DIRECTORY + "/eye-30.png";
    public static final String CLOUD_ICON = ASSETS_DIRECTORY + "/cloud-download-30.png";
    public static final String BAR_CHART_ICON = ASSETS_DIRECTORY + "/bar-chart-30.png";
    public static final String UNAVAILBLE_ICON = ASSETS_DIRECTORY + "/unavailable.png";
    public static final String NETWORK_ICON = ASSETS_DIRECTORY + "/globe-outline-white-30.png";
    public static final String NETWORK_ICON256 = ASSETS_DIRECTORY + "/globe-outline-white-120.png";
    public static final String TOGGLE_FRAME = ASSETS_DIRECTORY + "/expandFrame.png";


    public static Image iconImage15 = new Image(APP_ICON_15);
    public static Image logoImage256 = new Image(APP_LOGO_256);
    public static Image closeImg = new Image(CLOSE_ICON);
    public static Image minimizeImg = new Image(MINIMIZE_ICON);
    public static Image maximizeImg = new Image(MAXIMIZE_ICON);
    public static Image globeImg = new Image(NETWORK_ICON256);
    public static Image globeImage30 = new Image(NETWORK_ICON);
    public static Image settingsImg = new Image(SETTINGS_ICON_120);
    public static Image unknownImg = new Image(UNKNOWN_IMAGE_PATH);


    public final static double STAGE_WIDTH = 450;
    public final static double STAGE_HEIGHT = 250;
    
    public final static double SMALL_STAGE_WIDTH = 500;
    public final static double DEFAULT_STAGE_WIDTH = 700;
    public final static double DEFAULT_STAGE_HEIGHT = 500;

    public final static double BTN_IMG_SIZE = 30;
    public final static double MENU_BAR_IMAGE_WIDTH = 18;
    public final static int VIEWPORT_HEIGHT_OFFSET = 5;
    public final static int VIEWPORT_WIDTH_OFFSET = 5;
    public static final int ROW_HEIGHT = 27;
    public static final int MAX_ROW_HEIGHT = 20;
    public static final int COL_WIDTH = 160;


    public static String getStringFromResource(String resourceLocation) throws IOException{
        URL location = resourceLocation != null ? FxResourceFactory.class.getResource(resourceLocation) : null;
        if(location != null){
            try(
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                BufferedInputStream inStream = new BufferedInputStream(location.openStream());
            ){
                int bufferSize = 1024*8;
                byte[] buffer = new byte[bufferSize];
                int length = 0;

                while ((length = inStream.read(buffer)) != -1){
                    outStream.write(buffer, 0, length);
                }

                return outStream.toString();
            }
        }else{
            return null;
        }
    }
}
