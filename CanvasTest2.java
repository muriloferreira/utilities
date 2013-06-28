/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package javaapplication1;

import javax.media.opengl.awt.GLCanvas;
import java.awt.Dimension;
import java.lang.reflect.Field;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.glu.GLU;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;


/**
 *
 * @author murilo.ferreira
 */
public class CanvasTest2 extends GLCanvas implements GLEventListener {
   // Define constants for the top-level container
   private static String TITLE = "Rotating 3D Shaps (GLCanvas)";  // window's title
   private static final int CANVAS_WIDTH = 320;  // width of the drawable
   private static final int CANVAS_HEIGHT = 240; // height of the drawable
   private static final int FPS = 60; // animator's target frames per second
 
   /** The entry main() method to setup the top-level container and animator */
   public static void main(String[] args) {
       
       try {
       // carrega biblioteca jogl
            System.setProperty( "java.library.path", "C:/Users/murilo.ferreira/Documents/NetBeansProjects/JavaApplication1/jogl/windows-amd64" );
            Field fieldSysPath = ClassLoader.class.getDeclaredField( "sys_paths" );
            fieldSysPath.setAccessible( true );
            fieldSysPath.set( null, null );
       } catch (Exception ex) {
           ex.printStackTrace();
       }
       
      // Run the GUI codes in the event-dispatching thread for thread safety
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            // Create the OpenGL rendering canvas
            GLCanvas canvas = new CanvasTest2();
            canvas.setPreferredSize(new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT));
 

            // Create the top-level container
            final JFrame frame = new JFrame(); // Swing's JFrame or AWT's Frame
            frame.getContentPane().add(canvas);
            frame.setTitle(TITLE);
            frame.pack();
            frame.setVisible(true);

         }
      });
   }
 
   // Setup OpenGL Graphics Renderer
 
   private GLU glu;  // for the GL Utility
   private float anglePyramid = 0;    // rotational angle in degree for pyramid
   private float angleCube = 0;       // rotational angle in degree for cube
   private float speedPyramid = 2.0f; // rotational speed for pyramid
   private float speedCube = -1.5f;   // rotational speed for cube
 
   /** Constructor to setup the GUI for this Component */
   public CanvasTest2() {
      super();
      this.addGLEventListener(this);
   }
 
   // ------ Implement methods declared in GLEventListener ------
 
   /**
    * Called back immediately after the OpenGL context is initialized. Can be used
    * to perform one-time initialization. Run only once.
    */
   @Override
   public void init(GLAutoDrawable drawable) {
      GL2 gl = drawable.getGL().getGL2();      // get the OpenGL graphics context
      glu = new GLU();                         // get GL Utilities
      gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f); // set background (clear) color
      gl.glClearDepth(1.0f);      // set clear depth value to farthest
      gl.glEnable(GL2.GL_DEPTH_TEST); // enables depth testing
      gl.glDepthFunc(GL2.GL_LEQUAL);  // the type of depth test to do
      gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT, GL2.GL_NICEST); // best perspective correction
      gl.glShadeModel(GL2.GL_SMOOTH); // blends colors nicely, and smoothes out lighting
   }
 
   /**
    * Call-back handler for window re-size event. Also called when the drawable is
    * first set to visible.
    */
   @Override
   public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
      GL2 gl = drawable.getGL().getGL2();  // get the OpenGL 2 graphics context
 
      if (height == 0) height = 1;   // prevent divide by zero
      float aspect = (float)width / height;
 
      // Set the view port (display area) to cover the entire window
      gl.glViewport(0, 0, width, height);
 
      // Setup perspective projection, with aspect ratio matches viewport
      gl.glMatrixMode(GL2.GL_PROJECTION);  // choose projection matrix
      gl.glLoadIdentity();             // reset projection matrix
      glu.gluPerspective(45.0, aspect, 0.1, 100.0); // fovy, aspect, zNear, zFar
 
      // Enable the model-view transform
      gl.glMatrixMode(GL2.GL_MODELVIEW);
      gl.glLoadIdentity(); // reset
   }
 
   /**
    * Called back by the animator to perform rendering.
    */
   @Override
   public void display(GLAutoDrawable drawable) {
      GL2 gl = drawable.getGL().getGL2();  // get the OpenGL 2 graphics context
      gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT); // clear color and depth buffers
 
      // ----- Render the Color Cube -----
      gl.glLoadIdentity();                // reset the current model-view matrix
      gl.glTranslatef(1.6f, 0.0f, -7.0f); // translate right and into the screen
      gl.glRotatef(angleCube, 0.0f, 1.0f, 0.0f); // rotate about the x, y and z-axes
 
      gl.glBegin(GL2.GL_QUADS); // of the color cube
 
//      // Top-face
//      gl.glColor3f(0.0f, 1.0f, 0.0f); // green
//      gl.glVertex3f(1.0f, 1.0f, -1.0f);
//      gl.glVertex3f(-1.0f, 1.0f, -1.0f);
//      gl.glVertex3f(-1.0f, 1.0f, 1.0f);
//      gl.glVertex3f(1.0f, 1.0f, 1.0f);
 
//      // Bottom-face
//      gl.glColor3f(1.0f, 0.5f, 0.0f); // orange
//      gl.glVertex3f(1.0f, -1.0f, 1.0f);
//      gl.glVertex3f(-1.0f, -1.0f, 1.0f);
//      gl.glVertex3f(-1.0f, -1.0f, -1.0f);
//      gl.glVertex3f(1.0f, -1.0f, -1.0f);
 
//      // Front-face
//      gl.glColor3f(1.0f, 0.0f, 0.0f); // red
//      gl.glVertex3f(1.0f, 1.0f, 1.0f);
//      gl.glVertex3f(-1.0f, 1.0f, 1.0f);
//      gl.glVertex3f(-1.0f, -1.0f, 1.0f);
//      gl.glVertex3f(1.0f, -1.0f, 1.0f);
 
      // Back-face
      gl.glColor3f(1.0f, 1.0f, 0.0f); // yellow
      gl.glVertex3f(1.0f, -1.0f, -1.0f);
      gl.glVertex3f(-1.0f, -1.0f, -1.0f);
      gl.glVertex3f(-1.0f, 1.0f, -1.0f);
      gl.glVertex3f(1.0f, 1.0f, -1.0f);
 
      // Left-face
      gl.glColor3f(0.0f, 0.0f, 1.0f); // blue
      gl.glVertex3f(-1.0f, 1.0f, 1.0f);
      gl.glVertex3f(-1.0f, 1.0f, -1.0f);
      gl.glVertex3f(-1.0f, -1.0f, -1.0f);
      gl.glVertex3f(-1.0f, -1.0f, 1.0f);
 
      // Right-face
      gl.glColor3f(1.0f, 0.0f, 1.0f); // magenta
      gl.glVertex3f(1.0f, 1.0f, -1.0f);
      gl.glVertex3f(1.0f, 1.0f, 1.0f);
      gl.glVertex3f(1.0f, -1.0f, 1.0f);
      gl.glVertex3f(1.0f, -1.0f, -1.0f);
 
      gl.glEnd(); // of the color cube
 
      // Update the rotational angle after each refresh.
//      anglePyramid += speedPyramid; 
      angleCube += speedCube;
   }
 
   /**
    * Called back before the OpenGL context is destroyed. Release resource such as buffers.
    */
   @Override
   public void dispose(GLAutoDrawable drawable) { }
}

