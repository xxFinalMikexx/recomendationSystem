package com.example.xxfin.recommendationsystem;

/**
 * Created by xxfin on 18/04/2017.
 */

import android.app.Activity;

import com.nostra13.universalimageloader.core.ImageLoader;

/**
 * @author Paresh Mayani (@pareshmayani)
 */
public abstract class BaseActivity extends Activity {

    protected ImageLoader imageLoader = ImageLoader.getInstance();

}
