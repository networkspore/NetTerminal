package io.netnotes;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;
   


public class Main {
    private static class WindowTest{
        private long window;

        public void run() {
            System.out.println("Starting LWJGL " + org.lwjgl.Version.getVersion());

            init();
            loop();

            // Cleanup
            glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
            glfwTerminate();
            glfwSetErrorCallback(null).free();
        }

        private void init() {
            // Setup error callback
            GLFWErrorCallback.createPrint(System.err).set();

            // Initialize GLFW
            if (!glfwInit()) {
                throw new IllegalStateException("Unable to initialize GLFW");
            }

            // Configure GLFW
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // For macOS

            // Create window
            window = glfwCreateWindow(800, 600, "NanoVG Test Window", NULL, NULL);
            if (window == NULL) {
                throw new RuntimeException("Failed to create GLFW window");
            }

            // Setup key callback
            glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
                if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                    glfwSetWindowShouldClose(window, true);
                }
            });

            // Center window
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(
                window,
                (vidmode.width() - 800) / 2,
                (vidmode.height() - 600) / 2
            );

            // Make OpenGL context current
            glfwMakeContextCurrent(window);
            
            // Enable v-sync
            glfwSwapInterval(1);

            // Show window
            glfwShowWindow(window);

            // IMPORTANT: Make OpenGL bindings available
            GL.createCapabilities();

            System.out.println("OpenGL Version: " + glGetString(GL_VERSION));
        }

        private void loop() {
            // Clear color (dark gray background)
            glClearColor(0.2f, 0.2f, 0.2f, 1.0f);

            // Run until user closes window
            while (!glfwWindowShouldClose(window)) {
                // Clear the framebuffer
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                // Swap buffers and poll events
                glfwSwapBuffers(window);
                glfwPollEvents();
            }
        }
    }

    public static void main(String[] args) {
        new WindowTest().run();
    }
}


