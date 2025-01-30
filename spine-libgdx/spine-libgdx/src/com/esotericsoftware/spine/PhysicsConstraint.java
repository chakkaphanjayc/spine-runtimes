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

import static com.esotericsoftware.spine.utils.SpineUtils.*;

import com.esotericsoftware.spine.Skeleton.Physics;

/** Stores the current pose for a physics constraint. A physics constraint applies physics to bones.
 * <p>
 * See <a href="https://esotericsoftware.com/spine-physics-constraints">Physics constraints</a> in the Spine User Guide. */
public class PhysicsConstraint implements Updatable {
	final PhysicsConstraintData data;
	public Bone bone;
	float inertia, strength, damping, massInverse, wind, gravity, mix;

	boolean reset = true;
	float ux, uy, cx, cy, tx, ty;
	float xOffset, xVelocity;
	float yOffset, yVelocity;
	float rotateOffset, rotateVelocity;
	float scaleOffset, scaleVelocity;

	boolean active;

	final Skeleton skeleton;
	float remaining, lastTime;

	public PhysicsConstraint (PhysicsConstraintData data, Skeleton skeleton) {
		if (data == null) throw new IllegalArgumentException("data cannot be null.");
		if (skeleton == null) throw new IllegalArgumentException("skeleton cannot be null.");
		this.data = data;
		this.skeleton = skeleton;

		bone = skeleton.bones.get(data.bone.index);

		inertia = data.inertia;
		strength = data.strength;
		damping = data.damping;
		massInverse = data.massInverse;
		wind = data.wind;
		gravity = data.gravity;
		mix = data.mix;
	}

	/** Copy constructor. */
	public PhysicsConstraint (PhysicsConstraint constraint, Skeleton skeleton) {
		this(constraint.data, skeleton);

		inertia = constraint.inertia;
		strength = constraint.strength;
		damping = constraint.damping;
		massInverse = constraint.massInverse;
		wind = constraint.wind;
		gravity = constraint.gravity;
		mix = constraint.mix;
	}

	public void reset () {
		remaining = 0;
		lastTime = skeleton.time;
		reset = true;
		xOffset = 0;
		xVelocity = 0;
		yOffset = 0;
		yVelocity = 0;
		rotateOffset = 0;
		rotateVelocity = 0;
		scaleOffset = 0;
		scaleVelocity = 0;
	}

	public void setToSetupPose () {
		PhysicsConstraintData data = this.data;
		inertia = data.inertia;
		strength = data.strength;
		damping = data.damping;
		massInverse = data.massInverse;
		wind = data.wind;
		gravity = data.gravity;
		mix = data.mix;
	}

	/** Translates the physics constraint so next {@link #update(Physics)} forces are applied as if the bone moved an additional
	 * amount in world space. */
	public void translate (float x, float y) {
		ux -= x;
		uy -= y;
		cx -= x;
		cy -= y;
	}

	/** Rotates the physics constraint so next {@link #update(Physics)} forces are applied as if the bone rotated around the
	 * specified point in world space. */
	public void rotate (float x, float y, float degrees) {
		float r = degrees * degRad, cos = cos(r), sin = sin(r);
		float dx = cx - x, dy = cy - y;
		translate(dx * cos - dy * sin - dx, dx * sin + dy * cos - dy);
	}

	/** Applies the constraint to the constrained bones. */
	public void update (Physics physics) {
		float mix = this.mix;
		if (mix == 0) return;

		boolean x = data.x > 0, y = data.y > 0, rotateOrShearX = data.rotate > 0 || data.shearX > 0, scaleX = data.scaleX > 0;
		Bone bone = this.bone;
		float l = bone.data.length;

		switch (physics) {
		case none:
			return;
		case reset:
			reset();
			// Fall through.
		case update:
			Skeleton skeleton = this.skeleton;
			float delta = Math.max(skeleton.time - lastTime, 0);
			remaining += delta;
			lastTime = skeleton.time;

			float bx = bone.worldX, by = bone.worldY;
			if (reset) {
				reset = false;
				ux = bx;
				uy = by;
			} else {
				float a = remaining, i = inertia, t = data.step, f = skeleton.data.referenceScale, d = -1;
				float qx = data.limit * delta, qy = qx * Math.abs(skeleton.scaleY);
				qx *= Math.abs(skeleton.scaleX);
				if (x || y) {
					if (x) {
						float u = (ux - bx) * i;
						xOffset += u > qx ? qx : u < -qx ? -qx : u;
						ux = bx;
					}
					if (y) {
						float u = (uy - by) * i;
						yOffset += u > qy ? qy : u < -qy ? -qy : u;
						uy = by;
					}
					if (a >= t) {
						d = (float)Math.pow(damping, 60 * t);
						float m = massInverse * t, e = strength, w = wind * f * skeleton.scaleX, g = gravity * f * skeleton.scaleY;
						do {
							if (x) {
								xVelocity += (w - xOffset * e) * m;
								xOffset += xVelocity * t;
								xVelocity *= d;
							}
							if (y) {
								yVelocity -= (g + yOffset * e) * m;
								yOffset += yVelocity * t;
								yVelocity *= d;
							}
							a -= t;
						} while (a >= t);
					}
					if (x) bone.worldX += xOffset * mix * data.x;
					if (y) bone.worldY += yOffset * mix * data.y;
				}
				if (rotateOrShearX || scaleX) {
					float ca = atan2(bone.c, bone.a), c, s, mr = 0;
					float dx = cx - bone.worldX, dy = cy - bone.worldY;
					if (dx > qx)
						dx = qx;
					else if (dx < -qx) //
						dx = -qx;
					if (dy > qy)
						dy = qy;
					else if (dy < -qy) //
						dy = -qy;
					if (rotateOrShearX) {
						mr = (data.rotate + data.shearX) * mix;
						float r = atan2(dy + ty, dx + tx) - ca - rotateOffset * mr;
						rotateOffset += (r - (float)Math.ceil(r * invPI2 - 0.5f) * PI2) * i;
						r = rotateOffset * mr + ca;
						c = cos(r);
						s = sin(r);
						if (scaleX) {
							r = l * bone.getWorldScaleX();
							if (r > 0) scaleOffset += (dx * c + dy * s) * i / r;
						}
					} else {
						c = cos(ca);
						s = sin(ca);
						float r = l * bone.getWorldScaleX();
						if (r > 0) scaleOffset += (dx * c + dy * s) * i / r;
					}
					a = remaining;
					if (a >= t) {
						if (d == -1) d = (float)Math.pow(damping, 60 * t);
						float m = massInverse * t, e = strength, w = wind, g = gravity, h = l / f;
						while (true) {
							a -= t;
							if (scaleX) {
								scaleVelocity += (w * c - g * s - scaleOffset * e) * m;
								scaleOffset += scaleVelocity * t;
								scaleVelocity *= d;
							}
							if (rotateOrShearX) {
								rotateVelocity -= ((w * s + g * c) * h + rotateOffset * e) * m;
								rotateOffset += rotateVelocity * t;
								rotateVelocity *= d;
								if (a < t) break;
								float r = rotateOffset * mr + ca;
								c = cos(r);
								s = sin(r);
							} else if (a < t) //
								break;
						}
					}
				}
				remaining = a;
			}
			cx = bone.worldX;
			cy = bone.worldY;
			break;
		case pose:
			if (x) bone.worldX += xOffset * mix * data.x;
			if (y) bone.worldY += yOffset * mix * data.y;
		}

		if (rotateOrShearX) {
			float o = rotateOffset * mix, s, c, a;
			if (data.shearX > 0) {
				float r = 0;
				if (data.rotate > 0) {
					r = o * data.rotate;
					s = sin(r);
					c = cos(r);
					a = bone.b;
					bone.b = c * a - s * bone.d;
					bone.d = s * a + c * bone.d;
				}
				r += o * data.shearX;
				s = sin(r);
				c = cos(r);
				a = bone.a;
				bone.a = c * a - s * bone.c;
				bone.c = s * a + c * bone.c;
			} else {
				o *= data.rotate;
				s = sin(o);
				c = cos(o);
				a = bone.a;
				bone.a = c * a - s * bone.c;
				bone.c = s * a + c * bone.c;
				a = bone.b;
				bone.b = c * a - s * bone.d;
				bone.d = s * a + c * bone.d;
			}
		}
		if (scaleX) {
			float s = 1 + scaleOffset * mix * data.scaleX;
			bone.a *= s;
			bone.c *= s;
		}
		if (physics != Physics.pose) {
			tx = l * bone.a;
			ty = l * bone.c;
		}
		bone.updateAppliedTransform();
	}

	/** The bone constrained by this physics constraint. */
	public Bone getBone () {
		return bone;
	}

	public void setBone (Bone bone) {
		this.bone = bone;
	}

	public float getInertia () {
		return inertia;
	}

	public void setInertia (float inertia) {
		this.inertia = inertia;
	}

	public float getStrength () {
		return strength;
	}

	public void setStrength (float strength) {
		this.strength = strength;
	}

	public float getDamping () {
		return damping;
	}

	public void setDamping (float damping) {
		this.damping = damping;
	}

	public float getMassInverse () {
		return massInverse;
	}

	public void setMassInverse (float massInverse) {
		this.massInverse = massInverse;
	}

	public float getWind () {
		return wind;
	}

	public void setWind (float wind) {
		this.wind = wind;
	}

	public float getGravity () {
		return gravity;
	}

	public void setGravity (float gravity) {
		this.gravity = gravity;
	}

	/** A percentage (0-1) that controls the mix between the constrained and unconstrained poses. */
	public float getMix () {
		return mix;
	}

	public void setMix (float mix) {
		this.mix = mix;
	}

	public boolean isActive () {
		return active;
	}

	/** The physics constraint's setup pose data. */
	public PhysicsConstraintData getData () {
		return data;
	}

	public String toString () {
		return data.name;
	}
}
