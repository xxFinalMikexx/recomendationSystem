package com.example.xxfin.recommendationsystem;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.security.Principal;
import java.util.LinkedList;

public class PrincipalActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_principal);
    }

    public void showResultsHistory() {

    }

    public LinkedList checkHistory() {
        LinkedList resultados = new LinkedList();

        return resultados;
    }

    public void detectFaces(View v) {
        Intent intentFaces = new Intent(PrincipalActivity.this, DetectFacesActivity.class);
        startActivity(intentFaces);
    }
}
