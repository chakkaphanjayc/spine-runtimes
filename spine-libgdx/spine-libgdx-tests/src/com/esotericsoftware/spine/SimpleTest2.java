/******************************************************************************
 * Spine Runtimes License Agreement
 * Last updated February 20, 2024. Replaces all prior versions.
 *
 * Copyright (c) 2013-2024, Esoteric Software LLC
 *
 * Integration of the Spine Runtimes into software or otherwise creating
 * derivative works of the Spine Runtimes is permitted under the terms and
 * conditions of Section 2 of the Spine Editor License Agreement:
 * https://esotericsoftware.com/spine-editor-license
 *
 * Otherwise, it is permitted to integrate the Spine Runtimes into software or
 * otherwise create derivative works of the Spine Runtimes (collectively,
 * "Products"), provided that each user of the Products must obtain their own
 * Spine Editor license and redistribution of the Products in any form must
 * include this license and copyright notice.
 *
 * THE SPINE RUNTIMES ARE PROVIDED BY ESOTERIC SOFTWARE LLC "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL ESOTERIC SOFTWARE LLC BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES,
 * BUSINESS INTERRUPTION, OR LOSS OF USE, DATA, OR PROFITS) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THE
 * SPINE RUNTIMES, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *****************************************************************************/

package com.esotericsoftware.spine;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector3;

import com.esotericsoftware.spine.AnimationState.AnimationStateListener;
import com.esotericsoftware.spine.AnimationState.TrackEntry;
import com.esotericsoftware.spine.Skeleton.Physics;
import com.esotericsoftware.spine.attachments.BoundingBoxAttachment;
import com.esotericsoftware.spine.utils.TwoColorPolygonBatch;

/** Demonstrates loading, animating, and rendering a skeleton with hit detectiong using a bounding box attachment. */
public class SimpleTest2 extends ApplicationAdapter {
	OrthographicCamera camera;
	TwoColorPolygonBatch batch;
	SkeletonRenderer renderer;
	SkeletonRendererDebug debugRenderer;

	TextureAtlas atlas;
	Skeleton skeleton;
	SkeletonBounds bounds;
	AnimationState state;

	public void create () {
		camera = new OrthographicCamera();
		batch = new TwoColorPolygonBatch();
		renderer = new SkeletonRenderer();
		renderer.setPremultipliedAlpha(true);
		debugRenderer = new SkeletonRendererDebug();

		atlas = new TextureAtlas(Gdx.files.internal("spineboy/spineboy-pma.atlas"));
		SkeletonJson json = new SkeletonJson(atlas); // This loads skeleton JSON data, which is stateless.
		json.setScale(0.6f); // Load the skeleton at 60% the size it was in Spine.
		SkeletonData skeletonData = json.readSkeletonData(Gdx.files.internal("spineboy/spineboy-ess.json"));

		skeleton = new Skeleton(skeletonData); // Skeleton holds skeleton state (bone positions, slot attachments, etc).
		skeleton.setPosition(250, 20);
		skeleton.setAttachment("head-bb", "head"); // Attach "head" bounding box to "head-bb" slot.

		bounds = new SkeletonBounds(); // Convenience class to do hit detection with bounding boxes.

		AnimationStateData stateData = new AnimationStateData(skeletonData); // Defines mixing (crossfading) between animations.
		stateData.setMix("run", "jump", 0.2f);
		stateData.setMix("jump", "run", 0.2f);
		stateData.setMix("jump", "jump", 0);

		state = new AnimationState(stateData); // Holds the animation state for a skeleton (current animation, time, etc).
		state.setTimeScale(0.3f); // Slow all animations down to 30% speed.
		state.addListener(new AnimationStateListener() {

			public void start (TrackEntry entry) {
				System.out.println(entry.getTrackIndex() + " start: " + entry);
			}

			public void interrupt (TrackEntry entry) {
				System.out.println(entry.getTrackIndex() + " interrupt: " + entry);
			}

			public void end (TrackEntry entry) {
				System.out.println(entry.getTrackIndex() + " end: " + entry);
			}

			public void dispose (TrackEntry entry) {
				System.out.println(entry.getTrackIndex() + " dispose: " + entry);
			}

			public void complete (TrackEntry entry) {
				System.out.println(entry.getTrackIndex() + " complete: " + entry);
			}

			public void event (TrackEntry entry, Event event) {
				System.out
					.println(entry.getTrackIndex() + " event: " + entry + ", " + event.getData().getName() + ", " + event.getInt());
			}
		});

		// Set animation on track 0.
		state.setAnimation(0, "run", true);

		Gdx.input.setInputProcessor(new InputAdapter() {
			final Vector3 point = new Vector3();

			public boolean touchDown (int screenX, int screenY, int pointer, int button) {
				camera.unproject(point.set(screenX, screenY, 0)); // Convert window to world coordinates.
				bounds.update(skeleton, true); // Update SkeletonBounds with current skeleton bounding box positions.
				if (bounds.aabbContainsPoint(point.x, point.y)) { // Check if inside AABB first. This check is fast.
					BoundingBoxAttachment hit = bounds.containsPoint(point.x, point.y); // Check if inside a bounding box.
					if (hit != null) {
						System.out.println("hit: " + hit);
						skeleton.findSlot("head").getColor().set(Color.RED); // Turn head red until touchUp.
					}
				}
				return true;
			}

			public boolean touchUp (int screenX, int screenY, int pointer, int button) {
				skeleton.findSlot("head").getColor().set(Color.WHITE);
				return true;
			}

			public boolean keyDown (int keycode) {
				state.setAnimation(0, "jump", false); // Set animation on track 0 to jump.
				state.addAnimation(0, "run", true, 0); // Queue run to play after jump.
				return true;
			}
		});
	}

	public void render () {
		float delta = Gdx.graphics.getDeltaTime();
		state.update(delta); // Update the animation time.

		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		skeleton.update(delta); // Advance the skeleton time. This is needed when the skeleton has physics.
		if (state.apply(skeleton)) // Poses skeleton using current animations. This sets the bones' local SRT.
			skeleton.updateWorldTransform(Physics.update); // Uses the bones' local SRT to compute their world SRT.

		// Configure the camera, SpriteBatch, and SkeletonRendererDebug.
		camera.update();
		batch.getProjectionMatrix().set(camera.combined);
		debugRenderer.getShapeRenderer().setProjectionMatrix(camera.combined);

		batch.begin();
		renderer.draw(batch, skeleton); // Draw the skeleton images.
		batch.end();

		debugRenderer.draw(skeleton); // Draw debug lines.
	}

	public void resize (int width, int height) {
		camera.setToOrtho(false); // Update camera with new size.
	}

	public void dispose () {
		atlas.dispose();
	}

	public static void main (String[] args) throws Exception {
		new Lwjgl3Application(new SimpleTest2());
	}
}
