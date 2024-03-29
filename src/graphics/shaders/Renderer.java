/** 
 * The OpenGL renderer
 */

package graphics.shaders;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;
import android.widget.Toast;

@SuppressLint({ "FloatMath", "FloatMath" })
@TargetApi(8)
class Renderer implements GLSurfaceView.Renderer {
	/******************************
	 * PROPERTIES
	 ******************************/
	// rotation 
	public float mDX;
	public float mDY;
	float time = 0.0f;
	float distanceY = 0.0f;
	float accelY = 0.0f;

	private static final int FLOAT_SIZE_BYTES = 4;
	private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 8 * FLOAT_SIZE_BYTES;
	private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
	private static final int TRIANGLE_VERTICES_DATA_NOR_OFFSET = 3;
	private static final int TRIANGLE_VERTICES_DATA_TEX_OFFSET = 6;

	// shader constants
	private final int GOURAUD_SHADER = 0;
	private final int PHONG_SHADER = 1;
	private final int NORMALMAP_SHADER = 2;

	// array of shaders
	Shader _shaders[] = new Shader[3];
	private int _currentShader;

	/** Shader code **/
	private int[] vShaders;
	private int[] fShaders;

	// object constants
	private final int ROAD = 0;
	private final int CUBE = 1;

	// The objects
	Object3D[] _objects = new Object3D[2];

	// current object
	private int _currentObject;

	// Modelview/Projection matrices
	private float[] mMVPMatrix = new float[16];
	private float[] mProjMatrix = new float[16];
	private float[] mTransMatrix = new float[16];
	private float[] mMMatrix = new float[16];		// rotation
	private float[] mVMatrix = new float[16]; 		// modelview
	private float[] normalMatrix = new float[16]; 	// modelview normal

	// textures enabled?
	private boolean enableTexture = true;
	private int[] _texIDs;

	// light parameters
	private float[] lightPos;
	private float[] lightColor;
	private float[] lightAmbient;
	private float[] lightDiffuse;
	// angle rotation for light
	float angle = 0.0f;
	boolean lightRotate = false; 


	// material properties
	private float[] matAmbient;
	private float[] matDiffuse;
	private float[] matSpecular;
	private float matShininess;

	// eye pos
	private float[] eyePos = {0.0f, 10.0f, 25.0f};
	private float[] lookAt = {0.0f, 0.0f, 0.0f};
	private float[] lookDir = {
			lookAt[0] - eyePos[0],
			lookAt[1] - eyePos[1],
			lookAt[2] - eyePos[2]
	};
	//private float m_u = (float) Math.atan(lookDir[2] / lookDir[0]); 

	// scaling
	float scaleX = 1.0f;
	float scaleY = 1.0f;
	float scaleZ = 1.0f;



	private Context mContext;
	private static String TAG = "Renderer";

	/***************************
	 * CONSTRUCTOR(S)
	 **************************/
	public Renderer(Context context) {

		mContext = context;

		// setup all the shaders
		vShaders = new int[3];
		fShaders = new int[3];

		// basic - just gouraud shading
		vShaders[GOURAUD_SHADER] = R.raw.gouraud_vs;
		fShaders[GOURAUD_SHADER] = R.raw.gouraud_ps;

		// phong shading
		vShaders[PHONG_SHADER] = R.raw.phong_vs;
		fShaders[PHONG_SHADER] = R.raw.phong_ps;

		// normal mapping
		vShaders[NORMALMAP_SHADER] = R.raw.normalmap_vs;
		fShaders[NORMALMAP_SHADER] = R.raw.normalmap_ps;

		// Create some objects - pass in the textures, the meshes
		try {
			int[] normalMapTextures = {R.raw.diffuse_old, R.raw.diffusenormalmap_deepbig};
			_objects[0] = new Object3D(R.raw.road, false, context);
			_objects[1] = new Object3D(normalMapTextures, R.raw.texturedcube, true, context);
		} catch (Exception e) {
			//showAlert("" + e.getMessage());
		}

		// set current object and shader
		_currentObject = this.CUBE;
		_currentShader = this.GOURAUD_SHADER;
	}

	/*****************************
	 * GL FUNCTIONS
	 ****************************/
	
	
	@TargetApi(8)
	private void setLight(int _program){
		// rotate the light?
		if (lightRotate) {
			angle += 0.000005f;
			if (angle >= 6.2)
				angle = 0.0f;

			// rotate light about y-axis
			float newPosX = (float)(Math.cos(angle) * lightPos[0] - Math.sin(angle) * lightPos[2]);
			float newPosZ = (float)(Math.sin(angle) * lightPos[0] + Math.cos(angle) * lightPos[2]);
			lightPos[0] = newPosX; lightPos[2] = newPosZ;
		}
		
		// lighting variables
		// send to shaders
		GLES20.glUniform4fv(GLES20.glGetUniformLocation(_program, "lightPos"), 1, lightPos, 0);
		GLES20.glUniform4fv(GLES20.glGetUniformLocation(_program, "lightColor"), 1, lightColor, 0);

		// material 
		GLES20.glUniform4fv(GLES20.glGetUniformLocation(_program, "matAmbient"), 1, matAmbient, 0);
		GLES20.glUniform4fv(GLES20.glGetUniformLocation(_program, "matDiffuse"), 1, matDiffuse, 0);
		GLES20.glUniform4fv(GLES20.glGetUniformLocation(_program, "matSpecular"), 1, matSpecular, 0);
		GLES20.glUniform1f(GLES20.glGetUniformLocation(_program, "matShininess"), matShininess);
	}
	
	
	private void drawCar(int program, float[] startPos){
		Matrix.setIdentityM(mMMatrix, 0);
		Matrix.setIdentityM(mTransMatrix, 0);
		
		//K�perny� Y tengely
		if(Math.abs(mDY) < 2)
			mDY = 0;
		
		if(Math.abs(mDY) <= 250){
			accelY = -1 * mDY * 2;
		}
		else{
			if(mDY < 0)
				mDY = -250f;
			if(mDY > 0)
				mDY = 250f;
		}
			distanceY += accelY;
			Log.d("mDY:", String.valueOf(mDY));
			
			
		//K�perny� X tengely
		if(Math.abs(mDX) < 2)
			mDX = 0;
		float[] forward = {
				(float) Math.cos(mDX / 50),
				0.0f,
				(float) Math.sin(mDX / 50)
				};
		
		//lookAt			
		lookAt[0] = eyePos[0] + 25 * forward[0];
		lookAt[1] = eyePos[1] + 25 * forward[1];
		lookAt[2] = eyePos[2] + 25 * forward[2];
		
		
		//eyePos
		eyePos[0] = 0.0f + distanceY / 1000 * forward[0];
		eyePos[2] = 25.0f + distanceY / 1000 * forward[2];
		
		Matrix.setLookAtM(
				mVMatrix,
				0, 
				eyePos[0], eyePos[1], eyePos[2], 
				lookAt[0], lookAt[1], lookAt[2],
				0.0f, 1.0f, 0.0f);
			
		
		//Korm�nyz�s
		startPos[0] = eyePos[0] + 5 * forward[0];
		startPos[2] = eyePos[2] + 5 * forward[2];
		
		Matrix.translateM(mTransMatrix, 0, startPos[0], startPos[1], startPos[2]);
		
		Matrix.multiplyMM(mMMatrix, 0, mMMatrix, 0, mTransMatrix, 0);    //Translate
		Matrix.multiplyMM(mMVPMatrix, 0, mVMatrix, 0, mMMatrix, 0);      //View
		Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mMVPMatrix, 0); //Proj

		// send to the shader
		GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMVPMatrix"), 1, false, mMVPMatrix, 0);

		// Create the normal modelview matrix
		// Invert + transpose of mvpmatrix
		Matrix.invertM(normalMatrix, 0, mMVPMatrix, 0);
		Matrix.transposeM(normalMatrix, 0, normalMatrix, 0);

		// send to the shader
		GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "normalMatrix"), 1, false, mMVPMatrix, 0);
		
		
		/*** DRAWING OBJECT **/
		// Get buffers from mesh
		Object3D ob = this._objects[this.CUBE];
		Mesh mesh = ob.getMesh();
		FloatBuffer _vb = mesh.get_vb();
		ShortBuffer _ib = mesh.get_ib();

		short[] _indices = mesh.get_indices();

		// Vertex buffer

		// the vertex coordinates
		_vb.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
		GLES20.glVertexAttribPointer(GLES20.glGetAttribLocation(program, "aPosition"), 3, GLES20.GL_FLOAT, false,
				TRIANGLE_VERTICES_DATA_STRIDE_BYTES, _vb);
		GLES20.glEnableVertexAttribArray(GLES20.glGetAttribLocation(program, "aPosition"));

		// the normal info
		_vb.position(TRIANGLE_VERTICES_DATA_NOR_OFFSET);
		GLES20.glVertexAttribPointer(GLES20.glGetAttribLocation(program, "aNormal"), 3, GLES20.GL_FLOAT, false,
				TRIANGLE_VERTICES_DATA_STRIDE_BYTES, _vb);
		GLES20.glEnableVertexAttribArray(GLES20.glGetAttribLocation(program, "aNormal"));

		// Texture info

		// bind textures
		if (ob.hasTexture()) {// && enableTexture) {
			// number of textures
			int[] texIDs = ob.get_texID(); 
			
			for(int i = 0; i < _texIDs.length; i++) {
				GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
				//Log.d("TEXTURE BIND: ", i + " " + texIDs[i]);
				GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIDs[i]);
				GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "texture" + (i+1)), i);
			}
		}

		// enable texturing? [fix - sending float is waste]
		GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "hasTexture")/*shader.hasTextureHandle*/, ob.hasTexture() && enableTexture ? 2.0f : 0.0f);

		// texture coordinates
		_vb.position(TRIANGLE_VERTICES_DATA_TEX_OFFSET);
		GLES20.glVertexAttribPointer(GLES20.glGetAttribLocation(program, "textureCoord")/*shader.maTextureHandle*/, 2, GLES20.GL_FLOAT, false,
				TRIANGLE_VERTICES_DATA_STRIDE_BYTES, _vb);
		GLES20.glEnableVertexAttribArray(GLES20.glGetAttribLocation(program, "textureCoord"));//GLES20.glEnableVertexAttribArray(shader.maTextureHandle);

		// Draw with indices
		GLES20.glDrawElements(GLES20.GL_TRIANGLES, _indices.length, GLES20.GL_UNSIGNED_SHORT, _ib);
		checkGlError("glDrawElements");

		/** END DRAWING OBJECT ***/
		
	}
	
	
	private void drawCar2(int program, float[] startPos){
		Matrix.setIdentityM(mMMatrix, 0);
		Matrix.setIdentityM(mTransMatrix, 0);

		Matrix.translateM(mTransMatrix, 0, startPos[0], startPos[1], startPos[2]);
		
		Matrix.multiplyMM(mMMatrix, 0, mMMatrix, 0, mTransMatrix, 0);    //Translate
		Matrix.multiplyMM(mMVPMatrix, 0, mVMatrix, 0, mMMatrix, 0);      //View
		Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mMVPMatrix, 0); //Proj

		// send to the shader
		GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMVPMatrix"), 1, false, mMVPMatrix, 0);

		// Create the normal modelview matrix
		// Invert + transpose of mvpmatrix
		Matrix.invertM(normalMatrix, 0, mMVPMatrix, 0);
		Matrix.transposeM(normalMatrix, 0, normalMatrix, 0);

		// send to the shader
		GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "normalMatrix"), 1, false, mMVPMatrix, 0);
		
		
		/*** DRAWING OBJECT **/
		// Get buffers from mesh
		Object3D ob = this._objects[this.CUBE];
		Mesh mesh = ob.getMesh();
		FloatBuffer _vb = mesh.get_vb();
		ShortBuffer _ib = mesh.get_ib();

		short[] _indices = mesh.get_indices();

		// Vertex buffer

		// the vertex coordinates
		_vb.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
		GLES20.glVertexAttribPointer(GLES20.glGetAttribLocation(program, "aPosition"), 3, GLES20.GL_FLOAT, false,
				TRIANGLE_VERTICES_DATA_STRIDE_BYTES, _vb);
		GLES20.glEnableVertexAttribArray(GLES20.glGetAttribLocation(program, "aPosition"));

		// the normal info
		_vb.position(TRIANGLE_VERTICES_DATA_NOR_OFFSET);
		GLES20.glVertexAttribPointer(GLES20.glGetAttribLocation(program, "aNormal"), 3, GLES20.GL_FLOAT, false,
				TRIANGLE_VERTICES_DATA_STRIDE_BYTES, _vb);
		GLES20.glEnableVertexAttribArray(GLES20.glGetAttribLocation(program, "aNormal"));

		// Texture info

		// bind textures
		if (ob.hasTexture()) {// && enableTexture) {
			// number of textures
			int[] texIDs = ob.get_texID(); 
			
			for(int i = 0; i < _texIDs.length; i++) {
				GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
				//Log.d("TEXTURE BIND: ", i + " " + texIDs[i]);
				GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIDs[i]);
				GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "texture" + (i+1)), i);
			}
		}

		// enable texturing? [fix - sending float is waste]
		GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "hasTexture")/*shader.hasTextureHandle*/, ob.hasTexture() && enableTexture ? 2.0f : 0.0f);

		// texture coordinates
		_vb.position(TRIANGLE_VERTICES_DATA_TEX_OFFSET);
		GLES20.glVertexAttribPointer(GLES20.glGetAttribLocation(program, "textureCoord")/*shader.maTextureHandle*/, 2, GLES20.GL_FLOAT, false,
				TRIANGLE_VERTICES_DATA_STRIDE_BYTES, _vb);
		GLES20.glEnableVertexAttribArray(GLES20.glGetAttribLocation(program, "textureCoord"));//GLES20.glEnableVertexAttribArray(shader.maTextureHandle);

		// Draw with indices
		GLES20.glDrawElements(GLES20.GL_TRIANGLES, _indices.length, GLES20.GL_UNSIGNED_SHORT, _ib);
		checkGlError("glDrawElements");

		/** END DRAWING OBJECT ***/
		
	}
	
	
	
	
	private void drawRoad(int program){
		Matrix.setIdentityM(mMMatrix, 0);
		
		Matrix.multiplyMM(mMVPMatrix, 0, mVMatrix, 0, mMMatrix, 0);      //View
		Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mMVPMatrix, 0); //Proj
		
		// send to the shader
		GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMVPMatrix"), 1, false, mMVPMatrix, 0);

		// Create the normal modelview matrix
		// Invert + transpose of mvpmatrix
		Matrix.invertM(normalMatrix, 0, mMVPMatrix, 0);
		Matrix.transposeM(normalMatrix, 0, normalMatrix, 0);

		// send to the shader
		GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "normalMatrix"), 1, false, mMVPMatrix, 0);
		
		/*** DRAWING OBJECT **/
		// Get buffers from mesh
		Object3D ob = this._objects[this.ROAD];
		Mesh mesh = ob.getMesh();
		FloatBuffer _vb = mesh.get_vb();
		ShortBuffer _ib = mesh.get_ib();

		short[] _indices = mesh.get_indices();
		
		/*float[] myVertices = {
			-20, 0, -20,
			-20f, 0, 20,
			20, 0, -20,
			20, 0, 20
		};
		
		_vb = ByteBuffer.allocateDirect(myVertices.length
				* FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
		_vb.put(myVertices);*/

		// Vertex buffer

		// the vertex coordinates
		_vb.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
		GLES20.glVertexAttribPointer(GLES20.glGetAttribLocation(program, "aPosition"), 3, GLES20.GL_FLOAT, false,
				TRIANGLE_VERTICES_DATA_STRIDE_BYTES, _vb);
		GLES20.glEnableVertexAttribArray(GLES20.glGetAttribLocation(program, "aPosition"));

		// the normal info
		_vb.position(TRIANGLE_VERTICES_DATA_NOR_OFFSET);
		GLES20.glVertexAttribPointer(GLES20.glGetAttribLocation(program, "aNormal"), 3, GLES20.GL_FLOAT, false,
				TRIANGLE_VERTICES_DATA_STRIDE_BYTES, _vb);
		GLES20.glEnableVertexAttribArray(GLES20.glGetAttribLocation(program, "aNormal"));

		// Texture info

		// bind textures
		if (ob.hasTexture()) {// && enableTexture) {
			// number of textures
			int[] texIDs = ob.get_texID(); 
			
			for(int i = 0; i < _texIDs.length; i++) {
				GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
				//Log.d("TEXTURE BIND: ", i + " " + texIDs[i]);
				GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIDs[i]);
				GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "texture" + (i+1)), i);
			}
		}

		// enable texturing? [fix - sending float is waste]
		GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "hasTexture")/*shader.hasTextureHandle*/, ob.hasTexture() && enableTexture ? 2.0f : 0.0f);

		// texture coordinates
		_vb.position(TRIANGLE_VERTICES_DATA_TEX_OFFSET);
		GLES20.glVertexAttribPointer(GLES20.glGetAttribLocation(program, "textureCoord")/*shader.maTextureHandle*/, 2, GLES20.GL_FLOAT, false,
				TRIANGLE_VERTICES_DATA_STRIDE_BYTES, _vb);
		GLES20.glEnableVertexAttribArray(GLES20.glGetAttribLocation(program, "textureCoord"));//GLES20.glEnableVertexAttribArray(shader.maTextureHandle);

		// Draw with indices
		GLES20.glDrawElements(GLES20.GL_TRIANGLES, _indices.length, GLES20.GL_UNSIGNED_SHORT, _ib);
		checkGlError("glDrawElements");

		/** END DRAWING OBJECT ***/
		
	}
	
	
	/*
	 * Draw function - called for every frame
	 */
	public void onDrawFrame(GL10 glUnused) {
		// Ignore the passed-in GL10 interface, and use the GLES20
		// class's static methods instead.
		GLES20.glClearColor(.0f, .0f, .0f, 1.0f);
		GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

		GLES20.glUseProgram(0);
		// the current shader
		Shader shader = _shaders[this._currentShader]; // PROBLEM!
		int program = shader.get_program();
		
		// Start using the shader
		GLES20.glUseProgram(program);
		checkGlError("glUseProgram");
		
		setLight(program);		
		drawCar(program, new float[] {0.0f, 5.0f, 20.0f});
		drawCar2(program, new float[] {-10.0f, 5.0f, 10.0f});
		drawCar2(program, new float[] {-5.0f, 5.0f, -10.0f});
		drawCar2(program, new float[] {10.0f, 5.0f, 0.0f});
		drawCar2(program, new float[] {50.0f, 5.0f, 15.0f});
		drawRoad(program);
		
		
		// eye position
		GLES20.glUniform3fv(GLES20.glGetUniformLocation(program, "eyePos")/*shader.eyeHandle*/, 1, eyePos, 0);
	}

	/*
	 * Called when viewport is changed
	 * @see android.opengl.GLSurfaceView$Renderer#onSurfaceChanged(javax.microedition.khronos.opengles.GL10, int, int)
	 */
	public void onSurfaceChanged(GL10 glUnused, int width, int height) {
		GLES20.glViewport(0, 0, width, height);
		float ratio = (float) width / height;
		Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 0.5f, 40);
		//Matrix.frustumM(m, offset, left, right, bottom, top, near, far)
	}

	/**
	 * Initialization function
	 */
	public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
		// initialize shaders
		try {
			_shaders[GOURAUD_SHADER] = new Shader(vShaders[GOURAUD_SHADER], fShaders[GOURAUD_SHADER], mContext, false, 0); // gouraud
			_shaders[PHONG_SHADER] = new Shader(vShaders[PHONG_SHADER], fShaders[PHONG_SHADER], mContext, false, 0); // phong
			_shaders[NORMALMAP_SHADER] = new Shader(vShaders[NORMALMAP_SHADER], fShaders[NORMALMAP_SHADER], mContext, false, 0); // normal map
		} catch (Exception e) {
			Log.d("SHADER 0 SETUP", e.getLocalizedMessage());
		}

		GLES20.glEnable( GLES20.GL_DEPTH_TEST );
		GLES20.glClearDepthf(1.0f);
		GLES20.glDepthFunc( GLES20.GL_LEQUAL );
		GLES20.glDepthMask( true );

		// cull backface
		GLES20.glEnable( GLES20.GL_CULL_FACE );
		GLES20.glCullFace(GLES20.GL_BACK); 

		// light variables
		float[] lightP = {30.0f, 0.0f, 10.0f, 1};
		this.lightPos = lightP;

		float[] lightC = {0.5f, 0.5f, 0.5f};
		this.lightColor = lightC;

		// material properties
		float[] mA = {0.45f, 0.45f, 0.45f, 1.0f};
		matAmbient = mA;

		float[] mD = {0.6f, 0.6f, 0.6f, 1.0f};
		matDiffuse = mD;

		float[] mS =  {0.9f, 0.9f, 0.9f, 1.0f};
		matSpecular = mS;

		matShininess = 5.0f;

		// setup textures for all objects
		for(int i = 0; i < _objects.length; i++)
			setupTextures(_objects[i]);

		// set the view matrix
		
		Matrix.setLookAtM(
				mVMatrix,
				0, 
				eyePos[0], eyePos[1], eyePos[2], 
				0.0f, 0.0f, 0.0f, 
				0.0f, 1.0f, 0.0f);
		/*Matrix.setLookAtM(
			rm, 
			rmOffset, 
			eyeX, eyeY, eyeZ, 
			centerX, centerY, centerZ, 
			upX, upY, upZ)*/
	}

	/**************************
	 * OTHER METHODS
	 *************************/

	/**
	 * Changes the shader based on menu selection
	 * @param represents the other shader 
	 */
	public void setShader(int shader) {
		_currentShader = shader;
	}

	/**
	 * Changes the object based on menu selection
	 * @param represents the other object 
	 */
	public void setObject(int object) {
		_currentObject = object;
	}
	
	
	

	/**
	 * Show texture or not?
	 */
	public void flipTexturing() {
		enableTexture = !enableTexture;
		Object3D ob = _objects[this._currentObject];

		if (enableTexture && !ob.hasTexture()) {
			// Create a toast notification signifying that there is no texture associated with this object
			CharSequence text = "Object does not have associated texture";
			int duration = Toast.LENGTH_SHORT;

			Toast toast = Toast.makeText(mContext, text, duration);
			toast.show();
		}
		//this.toggleTexturing();
	}

	/**
	 * Rotate light or not?
	 */
	public void toggleLight() {
		this.lightRotate = !lightRotate;
		CharSequence text;
		if (lightRotate)
			text = "Light rotation resumed";
		else
			text = "Light rotation paused";
		int duration = Toast.LENGTH_SHORT;

		Toast toast = Toast.makeText(mContext, text, duration);
		toast.show();
	}

	/**
	 * Sets up texturing for the object
	 */
	private void setupTextures(Object3D ob) {
		// create new texture ids if object has them
		if (ob.hasTexture()) {
			// number of textures
			int[] texIDs = ob.get_texID();
			int[] textures = new int[texIDs.length];
			_texIDs = new int[texIDs.length];
			// texture file ids
			int[] texFiles = ob.getTexFile();

			Log.d("TEXFILES LENGTH: ", texFiles.length + "");
			GLES20.glGenTextures(texIDs.length, textures, 0);

			for(int i = 0; i < texIDs.length; i++) {
				texIDs[i] = textures[i];

				GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIDs[i]);

				// parameters
				GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
						GLES20.GL_NEAREST);
				GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
						GLES20.GL_TEXTURE_MAG_FILTER,
						GLES20.GL_LINEAR);

				GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
						GLES20.GL_REPEAT);
				GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
						GLES20.GL_REPEAT);

				InputStream is = mContext.getResources()
				.openRawResource(texFiles[i]);
				Bitmap bitmap;
				try {
					bitmap = BitmapFactory.decodeStream(is);
				} finally {
					try {
						is.close();
					} catch(IOException e) {
						// Ignore.
					}
				}

				// create it 
				GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
				bitmap.recycle();

				Log.d("ATTACHING TEXTURES: ", "Attached " + i);
			}
		}
	}

	/**
	 * Scaling
	 */
	public void changeScale(float scale) {
		if (scaleX * scale > 1.4f)
			return;
		scaleX *= scale;scaleY *= scale;scaleZ *= scale;

		Log.d("SCALE: ", scaleX + "");
	}

	// debugging opengl
	private void checkGlError(String op) {
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.e(TAG, op + ": glError " + error);
			throw new RuntimeException(op + ": glError " + error);
		}
	}

} 

// END CLASS