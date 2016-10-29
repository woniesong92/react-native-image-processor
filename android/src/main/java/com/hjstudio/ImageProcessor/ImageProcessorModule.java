package com.hjstudio.ImageProcessor;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageProcessorModule extends ReactContextBaseJavaModule {

  private int defaultQuality = 100;
  private final ReactApplicationContext mReactContext;

  public ImageProcessorModule(ReactApplicationContext reactContext) {

    super(reactContext);

    mReactContext = reactContext;
  }

  @Override
  public String getName() {
    return "ImageProcessor";
  }

  @ReactMethod
  public void resize(final String uriAsString, final int maxSize, final Callback mCallback) throws FileNotFoundException, IOException {
    WritableMap response = Arguments.createMap();

    Uri uri = Uri.parse(uriAsString);
    Bitmap resizedBitmap = getBitmapFromUri(uri, maxSize);

    File resizedImageFile = makeTmpFileFromBitmap(resizedBitmap);
    Uri resizedImageUri = Uri.fromFile(resizedImageFile);

    response.putString("uri", resizedImageUri.toString());

    mCallback.invoke(response);
  }

  private File makeTmpFileFromBitmap(final Bitmap bitmap) throws IOException {
    File outputDir = mReactContext.getExternalCacheDir();
    File outputFile = File.createTempFile("tmp", ".jpg", outputDir);
    FileOutputStream outputStream = new FileOutputStream(outputFile);

    bitmap.compress(Bitmap.CompressFormat.JPEG, defaultQuality, outputStream);
    outputStream.close();

    return outputFile;
  }

  private Bitmap getBitmapFromUri(final Uri uri, final int maxSize) throws FileNotFoundException, IOException {
    InputStream input = mReactContext.getContentResolver().openInputStream(uri);

    // Extract image info without decoding it completely
    // Use this info to get the ratio to be used in sampling
    BitmapFactory.Options onlyBoundsOptions = new BitmapFactory.Options();
    onlyBoundsOptions.inJustDecodeBounds = true;
    BitmapFactory.decodeStream(input, null, onlyBoundsOptions);
    input.close();

    if ((onlyBoundsOptions.outWidth == -1) || (onlyBoundsOptions.outHeight == -1)) {
      return null;
    }

    int originalSize = (onlyBoundsOptions.outHeight > onlyBoundsOptions.outWidth) ?
            onlyBoundsOptions.outHeight : onlyBoundsOptions.outWidth;

    double sampleRatio = (originalSize > maxSize) ? (originalSize / maxSize) : 1.0;

    return getSampleImage(uri, sampleRatio);
  }

  private Bitmap getSampleImage(final Uri uri, double sampleRatio) throws FileNotFoundException, IOException {
    InputStream input = mReactContext.getContentResolver().openInputStream(uri);

    BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
    bitmapOptions.inSampleSize = getPowerOfTwoForSampleRatio(sampleRatio);
    input = mReactContext.getContentResolver().openInputStream(uri);
    Bitmap resizedBitmap = BitmapFactory.decodeStream(input, null, bitmapOptions);
    input.close();

    // It's necessary to rotate the bitmap to its initial orientation
    // because the orientation is not preserved when it's sampled from input stream
    int rotationInDegrees = getOrientation(uri);
    Matrix matrix = new Matrix();
    matrix.preRotate(rotationInDegrees);

    return Bitmap.createBitmap(resizedBitmap, 0, 0, bitmapOptions.outWidth, bitmapOptions.outHeight, matrix, true);
  }

  //  http://stackoverflow.com/questions/8554264/how-to-use-exifinterface-with-a-stream-or-uri
  private int getOrientation(Uri uri) throws IOException {
    if (uri.toString().contains("content://")) {
      Cursor cursor = mReactContext.getContentResolver().query(uri,
              new String[] { MediaStore.Images.ImageColumns.ORIENTATION }, null, null, null);

      int result = -1;
      if (cursor != null) {
        if (cursor.moveToFirst()) {
          result = cursor.getInt(0);
        }
        cursor.close();
      }

      return result;
    } else {
      ExifInterface exif = new ExifInterface(uri.getPath());
      int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
      return exifToDegrees(rotation);
    }
  }

  private int exifToDegrees(int exifOrientation) {
    if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) { return 90; }
    else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {  return 180; }
    else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {  return 270; }
    return 0;
  }

  private static int getPowerOfTwoForSampleRatio(double ratio){
    int k = Integer.highestOneBit((int)Math.floor(ratio));
    if(k==0) return 1;
    else return k;
  }
}
