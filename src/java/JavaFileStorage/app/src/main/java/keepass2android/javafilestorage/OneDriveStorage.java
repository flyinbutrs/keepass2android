package keepass2android.javafilestorage;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.onedrive.sdk.concurrency.ICallback;
import com.onedrive.sdk.core.ClientException;
import com.onedrive.sdk.core.DefaultClientConfig;
import com.onedrive.sdk.core.IClientConfig;
import com.onedrive.sdk.extensions.IItemCollectionPage;
import com.onedrive.sdk.extensions.IOneDriveClient;
import com.onedrive.sdk.extensions.Item;
import com.onedrive.sdk.extensions.OneDriveClient;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Philipp on 20.11.2016.
 */
public class OneDriveStorage extends JavaFileStorageBase
{
    final keepass2android.javafilestorage.onedrive.MyMSAAuthenticator msaAuthenticator = new keepass2android.javafilestorage.onedrive.MyMSAAuthenticator() {
        @Override
        public String getClientId() {
            return "000000004010C234";
        }

        @Override
        public String[] getScopes() {
            return new String[] { "offline_access", "onedrive.readwrite" };
        }
    };

    IOneDriveClient oneDriveClient;

   public void bla(final Activity activity) {
        android.util.Log.d("KP2A", "0");

        android.util.Log.d("KP2A", "1");

        android.util.Log.d("KP2A", "2");
        oneDriveClient
                .getDrive()
                .getRoot()
                .buildRequest()
                .get(new ICallback<Item>() {
                    @Override
                    public void success(final Item result) {
                        final String msg = "Found Root " + result.id;
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void failure(ClientException ex) {
                        Toast.makeText(activity, ex.toString(), Toast.LENGTH_SHORT)
                                .show();
                    }
                });
        android.util.Log.d("KP2A", "3");

    }

    @Override
    public boolean requiresSetup(String path) {
        return !isConnected();
    }

    @Override
    public void startSelectFile(FileStorageSetupInitiatorActivity activity, boolean isForSave, int requestCode) {
        String path = getProtocolId()+":///";
        Log.d("KP2AJ", "startSelectFile "+path+", connected: "+path);
		if (isConnected())
		{
			Intent intent = new Intent();
			intent.putExtra(EXTRA_IS_FOR_SAVE, isForSave);
			intent.putExtra(EXTRA_PATH, path);
			activity.onImmediateResult(requestCode, RESULT_FILECHOOSER_PREPARED, intent);
		}
		else
        {
            activity.startSelectFileProcess(path, isForSave, requestCode);
        }
    }

    private boolean isConnected() {
        return msaAuthenticator.loginSilent() != null;
    }


    @Override
    public void prepareFileUsage(FileStorageSetupInitiatorActivity activity, String path, int requestCode, boolean alwaysReturnSuccess) {
        if (isConnected())
        {
            Intent intent = new Intent();
            intent.putExtra(EXTRA_PATH, path);
            activity.onImmediateResult(requestCode, RESULT_FILEUSAGE_PREPARED, intent);
        }
        else
        {
            activity.startFileUsageProcess(path, requestCode, alwaysReturnSuccess);
        }

    }

    @Override
    public String getProtocolId() {
        return "onedrive";
    }

    @Override
    public void prepareFileUsage(Context appContext, String path) throws UserInteractionRequiredException {
        if (!isConnected())
        {
            throw new UserInteractionRequiredException();
        }

    }

    @Override
    public void onCreate(FileStorageSetupActivity activity, Bundle savedInstanceState) {

        Log.d("KP2AJ", "OnCreate");

    }

    @Override
    public void onResume(FileStorageSetupActivity activity) {

        if (activity.getProcessName().equals(PROCESS_NAME_SELECTFILE))
            activity.getState().putString(EXTRA_PATH, activity.getPath());

        JavaFileStorage.FileStorageSetupActivity storageSetupAct = activity;

        if (storageSetupAct.getState().containsKey("hasStartedAuth"))
        {
            Log.d("KP2AJ", "auth started");


            if (oneDriveClient != null) {
                Log.d("KP2AJ", "auth successful");
                try {

                    finishActivityWithSuccess(activity);
                    return;

                } catch (Exception e) {
                    Log.d("KP2AJ", "finish with error: " + e.toString());
                    finishWithError(activity, e);
                    return;
                }
            }


            Log.i(TAG, "authenticating not succesful");
            Intent data = new Intent();
            data.putExtra(EXTRA_ERROR_MESSAGE, "authenticating not succesful");
            ((Activity)activity).setResult(Activity.RESULT_CANCELED, data);
            ((Activity)activity).finish();
        }
        else
        {
            Log.d("KP2AJ", "Starting auth");
            final IClientConfig oneDriveConfig = new DefaultClientConfig() { };

            oneDriveClient = new OneDriveClient.Builder()
                //.fromConfig(oneDriveConfig)
                .authenticator(msaAuthenticator)
                .executors(oneDriveConfig.getExecutors())
                .httpProvider(oneDriveConfig.getHttpProvider())
                .serializer(oneDriveConfig.getSerializer())
                .loginAndBuildClient((Activity)activity);

            storageSetupAct.getState().putBoolean("hasStartedAuth", true);

        }


    }


    String removeProtocol(String path)
    {
        if (path == null)
            return null;
        return path.substring(getProtocolId().length()+3);
    }

    @Override
    public String getDisplayName(String path) {
        return path;
    }

    @Override
    public String getFilename(String path) throws Exception {
        return path.substring(path.lastIndexOf("/")+1);
    }

    @Override
    public boolean checkForFileChangeFast(String path, String previousFileVersion) throws Exception {
        return false;
    }

    @Override
    public String getCurrentFileVersionFast(String path) {
        return null;
    }

    @Override
    public InputStream openFileForRead(String path) throws Exception {
        path = removeProtocol(path);
        return oneDriveClient.getDrive()
                .getRoot()
                .getItemWithPath(path)
                .getContent()
                .buildRequest()
                .get();
    }

    @Override
    public void uploadFile(String path, byte[] data, boolean writeTransactional) throws Exception {
        path = removeProtocol(path);
        oneDriveClient.getDrive()
                .getRoot()
                .getItemWithPath(path)
                .getContent()
                .buildRequest()
                .put(data);
    }

    @Override
    public String createFolder(String parentPath, String newDirName) throws Exception {
        throw new Exception("not implemented.");
    }

    @Override
    public String createFilePath(String parentPath, String newFileName) throws Exception {
        String path = parentPath;
        if (!path.endsWith("/"))
            path = path + "/";
        path = path + newFileName;

        return path;
    }

    @Override
    public List<FileEntry> listFiles(String parentPath) throws Exception {
        ArrayList<FileEntry> result = new ArrayList<FileEntry>();
        parentPath = removeProtocol(parentPath);
        IItemCollectionPage itemsPage = oneDriveClient.getDrive()
                .getRoot()
                .getItemWithPath(parentPath)
                .getChildren()
                .buildRequest()
                .get();
        while (true)
        {
            List<Item> items = itemsPage.getCurrentPage();
            if (items.isEmpty())
                return result;

            itemsPage = itemsPage.getNextPage().buildRequest().get();

            for (Item i: items)
            {
                FileEntry e = getFileEntry(getProtocolId() +"://"+ parentPath + "/" + i.name, i);
                result.add(e);
            }
        }
    }

    @NonNull
    private FileEntry getFileEntry(String path, Item i) {
        FileEntry e = new FileEntry();
        e.sizeInBytes = i.size;
        e.displayName = i.name;
        e.canRead = e.canWrite = true;
        e.path = path;
        e.isDirectory = i.folder != null;
        return e;
    }

    @Override
    public FileEntry getFileEntry(String filename) throws Exception {
        filename = removeProtocol(filename);
        Item item = oneDriveClient.getDrive()
                .getRoot()
                .getItemWithPath(filename)
                .buildRequest()
                .get();
        return getFileEntry(filename, item);
    }

    @Override
    public void delete(String path) throws Exception {
        path = removeProtocol(path);
        oneDriveClient.getDrive()
                .getRoot()
                .getItemWithPath(path)
                .buildRequest()
                .delete();
    }

    @Override
    public void onStart(FileStorageSetupActivity activity) {

    }

    @Override
    public void onActivityResult(FileStorageSetupActivity activity, int requestCode, int resultCode, Intent data) {

    }
}