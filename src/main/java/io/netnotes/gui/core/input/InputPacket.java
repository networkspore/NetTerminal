package io.netnotes.gui.core.input;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteInteger;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.utils.AtomicSequence;
import io.netnotes.gui.core.executors.Execs;

public class InputPacket {
     /**
     * Size in bytes (constant)
     */
    public static final int PACKET_SIZE = 32;


    
    private InputPacket(){}

    public static byte[] getNewPacket(){ return new byte[PACKET_SIZE]; }

  
    

    public static class Reader{
       
    }
    
   

    public static class InputTypes{
     // Mouse events (0-31)
        public static final NoteBytesReadOnly TYPE_MOUSE_MOVE_ABSOLUTE    = new NoteBytesReadOnly((short) 0);  // Screen coordinates
        public static final NoteBytesReadOnly TYPE_MOUSE_MOVE_RELATIVE    = new NoteBytesReadOnly((short) 1);  // Delta movement
        public static final NoteBytesReadOnly TYPE_MOUSE_BUTTON_DOWN      = new NoteBytesReadOnly((short) 2);
        public static final NoteBytesReadOnly TYPE_MOUSE_BUTTON_UP        = new NoteBytesReadOnly((short) 3);
        public static final NoteBytesReadOnly TYPE_SCROLL                 = new NoteBytesReadOnly((short) 4);
        
        // Keyboard events (32-63)
        public static final NoteBytesReadOnly TYPE_KEY_DOWN               = new NoteBytesReadOnly((short) 32);
        public static final NoteBytesReadOnly TYPE_KEY_UP                 = new NoteBytesReadOnly((short) 33);
        public static final NoteBytesReadOnly TYPE_KEY_CHAR               = new NoteBytesReadOnly((short) 34); // Unicode character
        
        // Touch events (64-95)
        public static final NoteBytesReadOnly TYPE_TOUCH                  = new NoteBytesReadOnly((short) 64);
        public static final NoteBytesReadOnly TYPE_TOUCH_DOWN             = new NoteBytesReadOnly((short) 65);
        public static final NoteBytesReadOnly TYPE_TOUCH_MOVE             = new NoteBytesReadOnly((short) 66);
        public static final NoteBytesReadOnly TYPE_TOUCH_UP               = new NoteBytesReadOnly((short) 67);
        public static final NoteBytesReadOnly TYPE_TOUCH_CANCEL           = new NoteBytesReadOnly((short) 68);
        public static final NoteBytesReadOnly TYPE_TOUCH_STATIONARY       = new NoteBytesReadOnly((short) 69);
        
        // Pen/Stylus events (96-127)
        public static final NoteBytesReadOnly TYPE_PEN_DOWN               = new NoteBytesReadOnly((short) 96);
        public static final NoteBytesReadOnly TYPE_PEN_MOVE               = new NoteBytesReadOnly((short) 97);
        public static final NoteBytesReadOnly TYPE_PEN_UP                 = new NoteBytesReadOnly((short) 98);
        public static final NoteBytesReadOnly TYPE_PEN_PRESSURE           = new NoteBytesReadOnly((short) 99);
        public static final NoteBytesReadOnly TYPE_PEN_TILT               = new NoteBytesReadOnly((short) 100);
        
        // Gamepad events (128-159)
        public static final NoteBytesReadOnly TYPE_GAMEPAD_BUTTON         = new NoteBytesReadOnly((short) 128);
        public static final NoteBytesReadOnly TYPE_GAMEPAD_AXIS           = new NoteBytesReadOnly((short) 129);
        public static final NoteBytesReadOnly TYPE_GAMEPAD_TRIGGER        = new NoteBytesReadOnly((short) 130);
        
        // Window/Focus events (160-191)
        public static final NoteBytesReadOnly TYPE_WINDOW_FOCUS_GAINED    = new NoteBytesReadOnly((short) 160);
        public static final NoteBytesReadOnly TYPE_WINDOW_FOCUS_LOST      = new NoteBytesReadOnly((short) 161);
        public static final NoteBytesReadOnly TYPE_WINDOW_MOVED           = new NoteBytesReadOnly((short) 162);
        public static final NoteBytesReadOnly TYPE_WINDOW_RESIZED         = new NoteBytesReadOnly((short) 163);
        
        // System events (192-223)
        public static final NoteBytesReadOnly TYPE_SYSTEM_SUSPEND         = new NoteBytesReadOnly((short) 192);
        public static final NoteBytesReadOnly TYPE_SYSTEM_RESUME          = new NoteBytesReadOnly((short) 193);
        
        // Custom/Extension (224-255)
        public static final NoteBytesReadOnly TYPE_CUSTOM_START           = new NoteBytesReadOnly((short) 224);

        // Scene lifecycle events (255-279)
        public static final NoteBytesReadOnly TYPE_SCENE_ATTACHED         = new NoteBytesReadOnly((short) 255);
        public static final NoteBytesReadOnly TYPE_SCENE_DETACHED         = new NoteBytesReadOnly((short) 256);
        public static final NoteBytesReadOnly TYPE_SCENE_SIZE_CHANGED     = new NoteBytesReadOnly((short) 257);
        
        // Stage lifecycle events (280-310)
        public static final NoteBytesReadOnly TYPE_STAGE_ATTACHED         = new NoteBytesReadOnly((short) 280);
        public static final NoteBytesReadOnly TYPE_STAGE_DETACHED         = new NoteBytesReadOnly((short) 281);
        public static final NoteBytesReadOnly TYPE_STAGE_SHOWN            = new NoteBytesReadOnly((short) 282);
        public static final NoteBytesReadOnly TYPE_STAGE_SHOWING          = new NoteBytesReadOnly((short) 283);
        public static final NoteBytesReadOnly TYPE_STAGE_HIDING           = new NoteBytesReadOnly((short) 284);
        public static final NoteBytesReadOnly TYPE_STAGE_HIDDEN           = new NoteBytesReadOnly((short) 285);
        public static final NoteBytesReadOnly TYPE_STAGE_CLOSING          = new NoteBytesReadOnly((short) 286);
        public static final NoteBytesReadOnly TYPE_STAGE_CLOSED           = new NoteBytesReadOnly((short) 287);
        public static final NoteBytesReadOnly TYPE_STAGE_ICONIFIED        = new NoteBytesReadOnly((short) 288);
        public static final NoteBytesReadOnly TYPE_STAGE_DEICONIFIED      = new NoteBytesReadOnly((short) 289);
        public static final NoteBytesReadOnly TYPE_STAGE_MAXIMIZED        = new NoteBytesReadOnly((short) 290);
        public static final NoteBytesReadOnly TYPE_STAGE_RESTORED         = new NoteBytesReadOnly((short) 291);
        public static final NoteBytesReadOnly TYPE_STAGE_FULLSCREEN       = new NoteBytesReadOnly((short) 292);
        public static final NoteBytesReadOnly TYPE_STAGE_POSITION_CHANGED = new NoteBytesReadOnly((short) 293);
        public static final NoteBytesReadOnly TYPE_STAGE_SIZE_CHANGED     = new NoteBytesReadOnly((short) 294);
        public static final NoteBytesReadOnly TYPE_STAGE_FOCUSED          = new NoteBytesReadOnly((short) 295);
        public static final NoteBytesReadOnly TYPE_STAGE_BLURRED          = new NoteBytesReadOnly((short) 296);
        public static final NoteBytesReadOnly TYPE_STAGE_ALWAYS_ON_TOP    = new NoteBytesReadOnly((short) 297);

        // Node events (350-360)
        public static final NoteBytesReadOnly TYPE_NODE_POSITION          = new NoteBytesReadOnly((short) 350);
        public static final NoteBytesReadOnly TYPE_NODE_SIZE              = new NoteBytesReadOnly((short) 351);
        public static final NoteBytesReadOnly TYPE_NODE_BOUNDS            = new NoteBytesReadOnly((short) 352);

        // lifecycle events (1000-1020)
        public static final NoteBytesReadOnly TYPE_STARTED                = new NoteBytesReadOnly((short) 1000);
        public static final NoteBytesReadOnly TYPE_STOPPED                = new NoteBytesReadOnly((short) 1001);
    }

 
    public static class Factory{
      //  public static final NoteBytesReadOnly SOURCE_ID_KEY     = new NoteBytesReadOnly("sId"); 
        public static final NoteBytesReadOnly TYPE_KEY          = new NoteBytesReadOnly("typ");
        public static final NoteBytesReadOnly SEQUENCE_KEY      = new NoteBytesReadOnly("seq"); 
        public static final NoteBytesReadOnly STATE_FLAGS_KEY   = new NoteBytesReadOnly("stF"); 
        public static final NoteBytesReadOnly PAYLOAD_KEY       = new NoteBytesReadOnly("pld"); 
       
        private final int BASE_BODY_SIZE = 2;

        private final NoteBytesReadOnly sourceId;

        public Factory(int sourceId) {
            this.sourceId =new NoteBytesReadOnly(sourceId);
        }

         public CompletableFuture<byte[]> ofAsync(NoteBytesReadOnly type) {
            return CompletableFuture.supplyAsync(()-> of(type, new NoteBytesReadOnly(AtomicSequence.getNextSequence())), Execs.getVirtualExecutor());
        }

    

        public byte[] of(NoteBytesReadOnly type, NoteBytesReadOnly atomicSequence, NoteBytesReadOnly... payload) {
            return of(type, atomicSequence, 0, payload);
        }

    
        /***
         * 
         * @param type InputPacket Type
         * @param stateFlags packet state flags
         * @param aux1 auxillary boolean1
         * @param aux2 auxillary boolean2
         * @param payload main payload
         * @return
         */
        public byte[] of(NoteBytesReadOnly type, NoteBytesReadOnly atomicSequence, int stateFlags, NoteBytesReadOnly... payload) {
           
                if(type == null){
                    throw new IllegalStateException("Type required");
                }
               
        
               
        
               
                boolean isStateFlags = stateFlags > 0;
                boolean isPayload = payload != null && payload.length > 0;

                int size = BASE_BODY_SIZE + (isStateFlags ? 1 : 0) + ((isPayload) ? 1 : 0);

                NoteBytesPair[] pairs = new NoteBytesPair[size]; 
                pairs[0] = new NoteBytesPair(TYPE_KEY, type);
                pairs[1] = new NoteBytesPair(SEQUENCE_KEY, atomicSequence);
      
                int index = BASE_BODY_SIZE;

                if(isStateFlags){
                    pairs[index] = new NoteBytesPair(STATE_FLAGS_KEY, new NoteInteger(stateFlags));
                }
                if(isPayload){
                    pairs[index++] = new NoteBytesPair(PAYLOAD_KEY, new NoteBytesArray(payload));
                }
                NoteBytesObject body = new NoteBytesObject(pairs);
                
                NoteBytesObject packet = new NoteBytesObject(new NoteBytesPair(sourceId, body));
                    
                return packet.getBytes();
    
        }
     }
   


     public static class MouseButtonTypes {
        private MouseButtonTypes() {}
      
        public static final byte NONE      = 0;
        public static final byte PRIMARY   = 1;
        public static final byte SECONDARY = 2;
        public static final byte MIDDLE    = 3;
        public static final byte BACK      = 4;
        public static final byte FORWARD   = 5;

    }
    

    public static class StateFlags {
        private StateFlags() {}

        /*
        * Bits 0–32  : state bits (modifiers, pressed/released/moved, etc.)
        */
        public static final int MOD_SHIFT   = 1 << 0;
        public static final int MOD_CTRL    = 1 << 1;
        public static final int MOD_ALT     = 1 << 2;
        public static final int MOD_META    = 1 << 3;
        public static final int MOD_CAPS    = 1 << 4;
        public static final int MOD_NUM     = 1 << 5;
        public static final int MOD_SCROLL  = 1 << 6;
        public static final int MOD_POPUP   = 1 << 7;
        public static final int MOD_STILL   = 1 << 8;


     
    }

}