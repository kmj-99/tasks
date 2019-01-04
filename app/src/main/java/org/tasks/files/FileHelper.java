package org.tasks.files;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.todoroo.astrid.utility.Constants;

import org.tasks.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import androidx.documentfile.provider.DocumentFile;

import static androidx.core.content.FileProvider.getUriForFile;
import static com.google.common.collect.Iterables.any;
import static com.todoroo.andlib.utility.AndroidUtilities.atLeastKitKat;
import static com.todoroo.andlib.utility.AndroidUtilities.atLeastLollipop;

public class FileHelper {

  public static Intent newFilePickerIntent(Activity activity, Uri initial, String... mimeTypes) {
    if (atLeastKitKat()) {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      if (initial != null) {
        intent.setData(initial);
      }
      if (mimeTypes.length == 1) {
        intent.setType(mimeTypes[0]);
      } else {
        intent.setType("*/*");
        if (mimeTypes.length > 1) {
          intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        }
      }
      return intent;
    } else {
      Intent intent = new Intent(activity, FileExplore.class);
      if (initial != null) {
        intent.putExtra(
            FileExplore.EXTRA_START_PATH,
            new File(initial.getPath()));
      }
      return intent;
    }
  }

  public static void newDirectoryPicker(Activity activity, int rc, @Nullable Uri initial) {
    if (atLeastLollipop()) {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
      intent.addFlags(
          Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
              | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
              | Intent.FLAG_GRANT_READ_URI_PERMISSION
              | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
      intent.putExtra("android.content.extra.SHOW_ADVANCED",true);
      activity.startActivityForResult(intent, rc);
    } else {
      Intent intent = new Intent(activity, FileExplore.class);
      intent.putExtra(FileExplore.EXTRA_DIRECTORY_MODE, true);
      if (initial != null) {
        intent.putExtra(
            FileExplore.EXTRA_START_PATH,
            new File(initial.getPath()));
      }
      activity.startActivityForResult(intent, rc);
    }
  }

  public static void delete(Context context, Uri uri) {
    if (uri == null) {
      return;
    }

    switch (uri.getScheme()) {
      case "content":
        DocumentFile documentFile = DocumentFile.fromSingleUri(context, uri);
        documentFile.delete();
        break;
      case "file":
        delete(new File(uri.getPath()));
        break;
    }
  }

  private static void delete(File... files) {
    if (files == null) {
      return;
    }

    for (File file : files) {
      if (file.isDirectory()) {
        delete(file.listFiles());
      } else {
        file.delete();
      }
    }
  }

  public static String getFilename(Context context, Uri uri) {
    switch (uri.getScheme()) {
      case ContentResolver.SCHEME_FILE:
        return uri.getLastPathSegment();
      case ContentResolver.SCHEME_CONTENT:
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
          try {
            return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
          } finally {
            cursor.close();
          }
        }
        break;
    }
    return null;
  }

  public static void startActionView(Activity context, Uri uri) {
    MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
    String filename = getFilename(context, uri);
    String extension = Files.getFileExtension(filename);
    String mimeType = mimeTypeMap.getMimeTypeFromExtension(extension);
    Intent intent = new Intent(Intent.ACTION_VIEW);
    if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
      uri = copyToUri(context, Uri.fromFile(context.getCacheDir()), uri);
    }
    Uri share = getUriForFile(context, Constants.FILE_PROVIDER_AUTHORITY, new File(uri.getPath()));
    intent.setDataAndType(share, mimeType);
    grantReadPermissions(context, intent, share);
    PackageManager packageManager = context.getPackageManager();
    if (intent.resolveActivity(packageManager) != null) {
      context.startActivity(intent);
    } else {
      Toast.makeText(context, R.string.no_application_found, Toast.LENGTH_SHORT).show();
    }
  }

  private static void grantReadPermissions(Context context, Intent intent, Uri uri) {
    if (atLeastLollipop()) {
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    } else {
      List<ResolveInfo> resolveInfoList =
          context
              .getPackageManager()
              .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
      for (ResolveInfo resolveInfo : resolveInfoList) {
        context.grantUriPermission(
            resolveInfo.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
      }
    }
  }

  public static Uri newFile(Context context, Uri destination, String mimeType, String baseName, String extension)
      throws IOException {
    String filename = getNonCollidingFileName(context, destination, baseName, extension);
    switch (destination.getScheme()) {
      case "content":
        DocumentFile tree = DocumentFile.fromTreeUri(context, destination);
        DocumentFile f1 = tree.createFile(mimeType, filename);
        if (f1 == null) {
          throw new FileNotFoundException("Failed to create " + filename);
        }
        return f1.getUri();
      case "file":
        File dir = new File(destination.getPath());
        if (!dir.exists() && !dir.mkdirs()) {
          throw new IOException("Failed to create %s" + dir.getAbsolutePath());
        }
        File f2 = new File(dir.getAbsolutePath() + File.separator + filename);
        if (f2.createNewFile()) {
          return Uri.fromFile(f2);
        }
        throw new FileNotFoundException("Failed to create " + filename);
      default:
        throw new IllegalArgumentException("Unknown URI scheme: " + destination.getScheme());
    }
  }

  public static Uri copyToUri(Context context, Uri destination, Uri input) {
    ContentResolver contentResolver = context.getContentResolver();
    MimeTypeMap mime = MimeTypeMap.getSingleton();
    String filename = getFilename(context, input);
    String baseName = Files.getNameWithoutExtension(filename);
    String extension = Files.getFileExtension(filename);
    String mimeType = mime.getMimeTypeFromExtension(extension);
    try {
      Uri output = newFile(context, destination, mimeType, baseName, extension);
      InputStream inputStream = contentResolver.openInputStream(input);
      OutputStream outputStream = contentResolver.openOutputStream(output);
      ByteStreams.copy(inputStream, outputStream);
      inputStream.close();
      outputStream.close();
      return output;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String getNonCollidingFileName(Context context, Uri uri, String baseName, String extension) {
    int tries = 1;
    if (!extension.startsWith(".")) {
      extension = "." + extension;
    }
    String tempName = baseName;
    switch (uri.getScheme()) {
      case ContentResolver.SCHEME_CONTENT:
        DocumentFile dir = DocumentFile.fromTreeUri(context, uri);
        List<DocumentFile> documentFiles = Arrays.asList(dir.listFiles());
        while (true) {
          String result = tempName + extension;
          if (any(documentFiles, f -> f.getName().equals(result))) {
            tempName = baseName + "-" + tries;
            tries++;
          } else {
            break;
          }
        }
        break;
      case ContentResolver.SCHEME_FILE:
        File f = new File(uri.getPath(), baseName + extension);
        while (f.exists()) {
          tempName = baseName + "-" + tries; // $NON-NLS-1$
          f = new File(uri.getPath(), tempName + extension);
          tries++;
        }
        break;
    }
    return tempName + extension;
  }
}
