package com.example.xxfin.recommendationsystem;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.location.Geocoder;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.xxfin.recommendationsystem.objects.*;
import com.google.android.gms.appdatasearch.GetRecentContextCall;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLng;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.cloud.vision.spi.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.FaceAnnotation;
import com.google.protobuf.ByteString;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import static android.provider.ContactsContract.ProviderStatus.STATUS;

public class DetectFacesActivity extends AppCompatActivity
        implements OnConnectionFailedListener {
    private static final int GALLERY_REQUEST = 1; // Codigo para identificar la llamada a la aplicación de galeria
    private static final int SEARCH_RADIOUS = 500; //Radio aproximado de búsqueda para Geocoder
    private static final int NEARBY_RADIOUS = 10000;
    private static final String API_KEY = "AIzaSyALTyezzge7Tz1HdQMfBrUyfkJMWdk_RCE";
    private static final String CLOUD_VISION_API_KEY = "AIzaSyCjh4AsNOB4sUyK_L46pXkYAajd832u96w";
    private static final String TAG = "DetectFaces Activity";

    private static final String ANDROID_PACKAGE_HEADER = "X-Android-Package";
    private static final String ANDROID_CERT_HEADER = "X-Android-Cert";

    private String rutaImagen;
    private double latitud = 0; // Variable para guardar latitud
    private double longitud = 0; // Variable para guardar longitud
    private Uri uriImagen;
    private String typeLocation;

    private GoogleApiClient mGoogleApiClient;

    private String placeId;
    private LinkedList jsonVision;
    private Places_Object placeData;
    private Place_Info placeInfo = new Place_Info();

    private HashMap mapNearbyPlaces = new HashMap();
    private LinkedList listNearbyPlaces = new LinkedList();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect_faces);

        mGoogleApiClient = new GoogleApiClient
                .Builder(this)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .enableAutoManage(this, this)
                .build();
        this.placeData = new Places_Object();

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
                this.rutaImagen = obtenerRutaRealUri(imagenSeleccionada); // Obtener ruta real de la imagen"
                Log.e(TAG, "Ruta: " + this.rutaImagen);
                Toast.makeText(DetectFacesActivity.this, "Cargando Imagen...",Toast.LENGTH_LONG).show();
                analizarDatosImagen(imagenSeleccionada);
                if (tieneCoordenadasImagen(rutaImagen)) {
                    Toast.makeText(DetectFacesActivity.this, "Lat: " + this.latitud + "\nLon " + this.longitud, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Coordenadas: \n" + this.latitud + "\n" + this.longitud);
                }
                else
                    Toast.makeText(DetectFacesActivity.this, "No tiene coordenadas", Toast.LENGTH_LONG).show();

            }
        } catch(Exception e) {
            Toast.makeText(DetectFacesActivity.this, "Error al obtener el resultado de la imagen", Toast.LENGTH_LONG).show();
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
                Toast.makeText(DetectFacesActivity.this, "Coordenadas encontradas", Toast.LENGTH_LONG).show();
                return true;
            }
        } catch (IOException e) {
            Toast.makeText(DetectFacesActivity.this, e.toString(), Toast.LENGTH_LONG).show();
        }

        return false;
    }

    public void analizarDatosImagen(Uri imagen) {
        /*Enviar request para Google Vision, devuelve un JSON con información del análisis*/
        enviarRequestVision(imagen);

        /*Obtener Place_Id del lugar usando coordenadas*/
        obtenerPlaceId();

        /*Obtener información del lugar basado en el Place_Id y lo guarda en un objeto de Place_Info*/
        obtenerInformación(this.placeId);

        /*Obtener información de los lugares similares al Place_Id*/
        obtenerResultadosSimilares(this.placeId);
    }

    public void enviarRequestVision(Uri imagen) {
        try {
            if (imagen != null) {
                Bitmap bitmap = scaleBitmapDown(MediaStore.Images.Media.getBitmap(getContentResolver(), imagen), 1200);
                callCloudVision(bitmap);
            } else {
                Log.d(TAG, "Imagen seleccionada nula");
                Toast.makeText(DetectFacesActivity.this, "Error al seleccionar imágen", Toast.LENGTH_LONG).show();
            }
        } catch(Exception e) {
            Toast.makeText(DetectFacesActivity.this, "Error al recuperar emociones", Toast.LENGTH_SHORT).show();
            Log.e(TAG, e.getMessage());
        }
    }

    public void obtenerPlaceId() {
        RequestQueue queue = Volley.newRequestQueue(this);

        StringBuilder googlePlacesUrl = new StringBuilder("http://maps.google.com/maps/api/geocode/json?");
        googlePlacesUrl.append("latlng=").append(latitud).append(",").append(longitud);
        googlePlacesUrl.append("&radious=").append(SEARCH_RADIOUS);
        googlePlacesUrl.append("&key=").append(API_KEY);

        JsonObjectRequest placeRequest = new JsonObjectRequest (
            Request.Method.GET,
            googlePlacesUrl.toString(),
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    //Toast.makeText(DetectFacesActivity.this, response.toString(), Toast.LENGTH_LONG).show();
                    parsePlaceId(response);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Toast.makeText(DetectFacesActivity.this, error.toString(), Toast.LENGTH_LONG).show();
                }
            }
        );
        queue.add(placeRequest);

        this.placeId = "";
    }

    public void obtenerInformación(String placeId) {
        RequestQueue queue = Volley.newRequestQueue(this);

        StringBuilder googlePlacesUrl = new StringBuilder("https://maps.googleapis.com/maps/api/place/details/json?");
        googlePlacesUrl.append("placeid=").append(latitud).append(",").append(longitud);
        googlePlacesUrl.append("&key=").append(API_KEY);

        final JsonObjectRequest placeRequest = new JsonObjectRequest (
                Request.Method.GET,
                googlePlacesUrl.toString(),
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        //Toast.makeText(DetectFacesActivity.this, response.toString(), Toast.LENGTH_LONG).show();
                        parsePlaceInformation(response);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(DetectFacesActivity.this, error.toString(), Toast.LENGTH_LONG).show();
            }
        });
        queue.add(placeRequest);
    }

    public void obtenerResultadosSimilares(String placeId) {
        RequestQueue queue = Volley.newRequestQueue(this);
        try {
            StringBuilder googlePlacesUrl = new StringBuilder("https://maps.googleapis.com/maps/api/place/nearbysearch/json?");
            //location=51.503186,-0.126446&radius=5000&types=hospital&key=AIzaSyALTyezzge7Tz1HdQMfBrUyfkJMWdk_RCE
            googlePlacesUrl.append("location=").append(latitud).append(",").append(longitud);
            googlePlacesUrl.append("&radious=").append(NEARBY_RADIOUS);
            googlePlacesUrl.append("&types=").append(this.placeInfo.getPlaceTypes().get(0));
            googlePlacesUrl.append("&key=").append(API_KEY);

            final JsonObjectRequest detailsRequest = new JsonObjectRequest(
                    Request.Method.GET,
                    googlePlacesUrl.toString(),
                    null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            //Toast.makeText(DetectFacesActivity.this, response.toString(), Toast.LENGTH_LONG).show();
                            parseInformationDetail(response);
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Toast.makeText(DetectFacesActivity.this, error.toString(), Toast.LENGTH_LONG).show();
                }
            });
            queue.add(detailsRequest);
        } catch(Exception e) {
            Toast.makeText(DetectFacesActivity.this, "Error en string NearbySearch", Toast.LENGTH_LONG).show();
        }
    }

    public void parsePlaceId(JSONObject result) {
        try {
            JSONArray jsonArray = result.getJSONArray("results");
            JSONObject place = jsonArray.getJSONObject(0);
            this.placeId = place.getString("place_id");
        } catch(Exception e) {
            Toast.makeText(DetectFacesActivity.this, e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    public void parsePlaceInformation(JSONObject result) {
        try {
            JSONArray jsonArray = result.getJSONArray("result");
            JSONObject place = jsonArray.getJSONObject(0);

            this.placeInfo.setName(place.getString("name"));
            this.placeInfo.setPlaceId(place.getString("place_id"));

            JSONObject geometry = place.getJSONObject("geometry").getJSONObject("location");
            LatLng coordinates = new LatLng(geometry.getDouble("latitude"), geometry.getDouble("longitude"));
            this.placeInfo.setLatlng(coordinates);

            this.placeInfo.setPlaceTypes(place.getJSONArray("types"));

            this.placeInfo.setRating(place.getDouble("rating"));

            Toast.makeText(DetectFacesActivity.this, "Información del lugar lista", Toast.LENGTH_LONG).show();
        } catch(Exception e) {
            Toast.makeText(DetectFacesActivity.this, e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    public void parseInformationDetail(JSONObject result) {
        try {
            JSONArray jsonArray = result.getJSONArray("results");
            for (int i = 1; i < jsonArray.length(); i++) {
                Place_Info actualPlace = new Place_Info();

                JSONObject place = jsonArray.getJSONObject(i);
                actualPlace.setName(place.getString("name"));
                actualPlace.setPlaceTypes(place.getJSONArray("types"));

                JSONObject geometry = place.getJSONObject("geometry").getJSONObject("location");
                LatLng coordinates = new LatLng(geometry.getDouble("latitude"), geometry.getDouble("longitude"));
                actualPlace.setLatlng(coordinates);

                actualPlace.setPlaceId(place.getString("place_id"));
                actualPlace.setRating(place.getDouble("rating"));

                mapNearbyPlaces.put(place.getString("place_id"), actualPlace);
                listNearbyPlaces.addLast(actualPlace);
                Log.e(TAG, actualPlace.toString());
            }

        } catch (Exception e) {
            Toast.makeText(DetectFacesActivity.this, "Error al obtener información de lugares cercanos", Toast.LENGTH_LONG).show();
        }

    }

    public Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {
        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }

    private void callCloudVision(final Bitmap bitmap) throws IOException {
        // Switch text to loading
        Log.e(TAG, "Cargando mensaje...");
        //mImageDetails.setText(R.string.loading_message);

        // Do the real work in an async task, because we need to use the network anyway
        new AsyncTask<Object, Void, String>() {
            @Override
            protected String doInBackground(Object... params) {
                try {
                    HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
                    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                    VisionRequestInitializer requestInitializer =
                            new VisionRequestInitializer(CLOUD_VISION_API_KEY) {
                                /**
                                 * We override this so we can inject important identifying fields into the HTTP
                                 * headers. This enables use of a restricted cloud platform API key.
                                 */
                                @Override
                                protected void initializeVisionRequest(VisionRequest<?> visionRequest)
                                        throws IOException {
                                    super.initializeVisionRequest(visionRequest);

                                    String packageName = getPackageName();
                                    visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);

                                    String sig = PackageManagerUtils.getSignature(getPackageManager(), packageName);

                                    visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);
                                }
                            };

                    Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
                    builder.setVisionRequestInitializer(requestInitializer);

                    Vision vision = builder.build();

                    BatchAnnotateImagesRequest batchAnnotateImagesRequest = new BatchAnnotateImagesRequest();
                    batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
                        AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

                        // Add the image
                        Image base64EncodedImage = new Image();
                        // Convert the bitmap to a JPEG
                        // Just in case it's a format that Android understands but Cloud Vision
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                        byte[] imageBytes = byteArrayOutputStream.toByteArray();

                        // Base64 encode the JPEG
                        base64EncodedImage.encodeContent(imageBytes);
                        annotateImageRequest.setImage(base64EncodedImage);

                        // add the features we want
                        annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                            Feature faceDetection = new Feature();
                            faceDetection.setType("FACE_DETECTION");
                            add(faceDetection);
                        }});

                        // Add the list of one thing to the request
                        add(annotateImageRequest);
                    }});

                    Vision.Images.Annotate annotateRequest =
                            vision.images().annotate(batchAnnotateImagesRequest);
                    // Due to a bug: requests to Vision API containing large images fail when GZipped.
                    annotateRequest.setDisableGZipContent(true);
                    Log.d(TAG, "created Cloud Vision request object, sending request");

                    BatchAnnotateImagesResponse response = annotateRequest.execute();
                    return convertResponseToString(response);

                } catch (GoogleJsonResponseException e) {
                    Log.d(TAG, "failed to make API request because " + e.getContent());
                } catch (IOException e) {
                    Log.d(TAG, "failed to make API request because of other IOException " +
                            e.getMessage());
                }
                return "Cloud Vision API request failed. Check logs for details.";
            }

            protected void onPostExecute(String result) {
                //mImageDetails.setText(result);
                Toast.makeText(DetectFacesActivity.this, result.toString(), Toast.LENGTH_LONG).show();
                Log.e(TAG, result.toString());
            }
        }.execute();
    }

    private String convertResponseToString(BatchAnnotateImagesResponse response) {
        String message = "I found these things:\n\n";

        List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();
        if (labels != null) {
            for (EntityAnnotation label : labels) {
                Log.e(TAG, label.toString());
                message += String.format(Locale.US, "%.3f: %s", label.getScore(), label.getDescription());
                message += "\n";
            }
        } else {
            message += "nothing";
        }

        return message;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}
