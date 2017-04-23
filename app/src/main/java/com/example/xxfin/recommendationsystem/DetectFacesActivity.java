package com.example.xxfin.recommendationsystem;

import android.content.Intent;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import java.io.IOException;

public class DetectFacesActivity extends AppCompatActivity {
    private static final int GALLERY_REQUEST = 1; // Codigo para identificar la llamada a la aplicación de galeria
    private String rutaImagen;
    private double latitud = 0; // Variable para guardar latitud
    private double longitud = 0; // Variable para guardar longitud
    private Uri uriImagen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect_faces);

        fotoPrueba();
    }

    public void fotoPrueba() {
        Intent intentGaleria = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI); // Se crea el intent para abrir la aplicación de galería
        startActivityForResult(intentGaleria, GALLERY_REQUEST); // Inicia la aplicación de galeria
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {

            if (requestCode == GALLERY_REQUEST && resultCode == RESULT_OK && data != null) { // Caso en el que la imagen se escoge de la galeria

                Uri imagenSeleccionada = data.getData(); // Obtener la información de la imagen
                this.rutaImagen = obtenerRutaRealUri(imagenSeleccionada); // Obtener ruta real de la imagen

                if (tieneCoordenadasImagen(rutaImagen)) {
                    Toast.makeText(DetectFacesActivity.this, "Lat: " + this.latitud + "\nLon " + this.longitud, Toast.LENGTH_LONG).show();
                }
                else
                    Toast.makeText(DetectFacesActivity.this, "No tiene coordenadas", Toast.LENGTH_LONG).show();

            }
        } catch(Exception e) {

        }
    }

    public String obtenerRutaRealUri(Uri imagenSeleccionada){
        try {
            String[] informacion_imagen = {MediaStore.Images.Media.DATA}; // Obtener la metadata de todas las imagenes guardadas en el dispositivo
            Cursor cursor = getContentResolver().query(imagenSeleccionada, informacion_imagen, null, null, null); // Buscar la imagen que coincide con el Uri dado
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);  // Buscar la columna de url de imagen
            cursor.moveToFirst(); // Ir al primer elemento
            return cursor.getString(column_index); // Regresar ruta real
        } catch (Exception e) {
            return imagenSeleccionada.getPath(); // Regresar ruta decodificada
        }
    }

    public boolean tieneCoordenadasImagen(String rutaImagen){
        try {
            float[] coordenadas = new float[2]; // Variable para guardar las coordenadas de la imagen
            ExifInterface exifInterface = new ExifInterface(rutaImagen); // Crear objeto para leer metadata de imagen
            if(exifInterface.getLatLong(coordenadas)) {
                this.latitud = (double) coordenadas[0];
                this.longitud = (double) coordenadas[1];
                return true;
            }
        } catch (IOException e) {
            Toast.makeText(DetectFacesActivity.this, e.toString(), Toast.LENGTH_LONG).show();
        }

        return false;
    }
}
