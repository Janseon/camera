package com.android.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.TextView;

public class RotateTextView extends TextView {
	private static final String NAMESPACE = "http://www.duoyi.com";
	private static final String ATTR_ROTATE = "rotate";
	private static final int DEFAULTVALUE_DEGREES = 0;
	private int degree;

	public RotateTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
		degree = attrs.getAttributeIntValue(NAMESPACE, ATTR_ROTATE,
				DEFAULTVALUE_DEGREES);
	}

	public void setDegree(int degree) {
		this.degree = degree;
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		canvas.rotate(degree, getWidth() / 2, getHeight() / 2);
		super.onDraw(canvas);
	}

}