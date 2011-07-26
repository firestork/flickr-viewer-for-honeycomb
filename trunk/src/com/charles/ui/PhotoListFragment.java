/**
 * 
 */

package com.charles.ui;

import com.aetrion.flickr.photos.Photo;
import com.aetrion.flickr.photos.PhotoList;
import com.charles.FlickrViewerActivity;
import com.charles.FlickrViewerApplication;
import com.charles.R;
import com.charles.actions.GetPhotoDetailAction;
import com.charles.dataprovider.PaginationPhotoListDataProvider;
import com.charles.event.FlickrViewerMessage;
import com.charles.event.IFlickrViewerMessageHandler;
import com.charles.event.IPhotoListReadyListener;
import com.charles.task.AsyncPhotoListTask;
import com.charles.task.ImageDownloadTask;
import com.charles.ui.comp.IContextMenuHandler;
import com.charles.utils.Constants;
import com.charles.utils.ImageCache;
import com.charles.utils.ImageUtils.DownloadedDrawable;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * @author charles
 */
public class PhotoListFragment extends Fragment implements
        AdapterView.OnItemClickListener, IPhotoListReadyListener, IFlickrViewerMessageHandler {

    private static final String BUNDLE_ATTR_DATA_PROVIDER = "data.provider"; //$NON-NLS-1$
    private static final String TAG = PhotoListFragment.class.getSimpleName();

    private PhotoList mPhotoList;
    private MyAdapter mGridAdapter;
    private GridView mGridView;

    private int mCurrentGridColumnCount = Constants.DEF_GRID_COL_COUNT;

    /**
     * The current page number.
     */
    private int mCurrentPageNumber = 1;

    /**
     * Remember the previous page number, when get photo task is canceled,
     * restore the <code>mCurrentPageNumber</code>
     */
    private int mOldPageNumber = 1;

    /**
     * The photo list data provider.
     */
    private PaginationPhotoListDataProvider mPhotoListDataProvider;

    /**
     * The async task to fetch photo list.
     */
    private AsyncPhotoListTask mPhotoListTask = null;

    /**
     * The current selected photo.
     */
    private Photo mSelectedPhoto;

    private IContextMenuHandler mContextMenuHandler = null;

    /**
     * Default constructor.
     */
    public PhotoListFragment() {
        mPhotoList = new PhotoList();
    }

    /**
     * Constructor with a list of photos.
     * 
     * @param photoList
     */
    public PhotoListFragment(PhotoList photoList,
            PaginationPhotoListDataProvider photoListDataProvider) {
        this.mPhotoList = photoList;
        this.mPhotoListDataProvider = photoListDataProvider;
    }

    /**
     * Constructor.
     * 
     * @param photoList
     * @param photoListDataProvider
     * @param menuHandler the context menu handler to add context menu for each
     *            grid item.
     */
    public PhotoListFragment(PhotoList photoList,
            PaginationPhotoListDataProvider photoListDataProvider, IContextMenuHandler menuHandler) {
        this.mPhotoList = photoList;
        this.mPhotoListDataProvider = photoListDataProvider;
        this.mContextMenuHandler = menuHandler;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        // handle the case that this fragment is created by the os.
        if (savedInstanceState != null) {
            PaginationPhotoListDataProvider savedDataProvider = (PaginationPhotoListDataProvider) savedInstanceState
                    .getSerializable(BUNDLE_ATTR_DATA_PROVIDER);
            if (mPhotoListDataProvider == null) {
                mPhotoListDataProvider = savedDataProvider;
                this.runPhotoListTask();
            }
        }
        View mRootContainer = (View) inflater.inflate(R.layout.photo_grid_view,
                null);

        // grid view.
        mGridView = (GridView) mRootContainer.findViewById(R.id.grid);
        FlickrViewerApplication app = (FlickrViewerApplication) getActivity()
                .getApplication();
        mCurrentGridColumnCount = app.getGridNumColumns();
        mGridView.setNumColumns(mCurrentGridColumnCount);
        mGridAdapter = new MyAdapter(getActivity(), mPhotoList);
        mGridView.setAdapter(mGridAdapter);
        mGridView.setOnItemClickListener(this);

        if (mContextMenuHandler != null) {
            mGridView.setOnCreateContextMenuListener(mContextMenuHandler);
        }

        // change action bar title
        FlickrViewerActivity act = (FlickrViewerActivity) getActivity();
        act.changeActionBarTitle(mPhotoListDataProvider.getDescription(getActivity()));
        return mRootContainer;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_photo_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        FlickrViewerApplication app = (FlickrViewerApplication) getActivity()
                .getApplication();
        int pageSize = app.getPageSize();

        switch (item.getItemId()) {
            case R.id.menu_item_previous_page:
                if (mCurrentPageNumber <= 1) {
                    Toast.makeText(getActivity(),
                            getActivity().getResources().getString(R.string.toast_first_page),
                            Toast.LENGTH_SHORT).show();
                } else {
                    mOldPageNumber = mCurrentPageNumber;
                    mCurrentPageNumber--;
                    mPhotoListDataProvider.setPageNumber(mCurrentPageNumber);
                    mPhotoListDataProvider.setPageSize(pageSize);
                    runPhotoListTask();
                }
                return true;
            case R.id.menu_item_next_page:
                if (mPhotoList.size() < pageSize) {
                    Toast.makeText(getActivity(),
                            getActivity().getResources().getString(R.string.toast_last_page),
                            Toast.LENGTH_SHORT).show();
                } else {
                    mOldPageNumber = mCurrentPageNumber;
                    mCurrentPageNumber++;
                    mPhotoListDataProvider.setPageNumber(mCurrentPageNumber);
                    mPhotoListDataProvider.setPageSize(pageSize);
                    runPhotoListTask();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Fetches the photo list in another thread.
     */
    private void runPhotoListTask() {
        if (mPhotoListTask != null) {
            mPhotoListTask.cancel(true);
        }
        mPhotoListTask = new AsyncPhotoListTask(getActivity(),
                    mPhotoListDataProvider, this);
        mPhotoListTask.execute();
    }

    private static class MyAdapter extends BaseAdapter {

        private PhotoList mPhotoList;
        private Context mContext;

        public MyAdapter(Context context, PhotoList mPhotoList) {
            this.mPhotoList = mPhotoList;
            this.mContext = context;
        }

        @Override
        public int getCount() {
            return mPhotoList.size();
        }

        @Override
        public Object getItem(int arg0) {
            return mPhotoList.get(arg0);
        }

        @Override
        public long getItemId(int arg0) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            View view = convertView;
            if (view == null) {
                LayoutInflater li = LayoutInflater.from(mContext);
                view = li.inflate(R.layout.interesting_list_item, null);
            }

            Photo photo = (Photo) getItem(position);

            ImageView photoImage;
            TextView titleView;

            ViewHolder holder = (ViewHolder) view.getTag();
            if (holder == null) {
                photoImage = (ImageView) view.findViewById(R.id.small_img);
                titleView = (TextView) view.findViewById(R.id.title);

                holder = new ViewHolder();
                holder.image = photoImage;
                holder.titleView = titleView;
                view.setTag(holder);

            } else {
                photoImage = holder.image;
                titleView = holder.titleView;
            }
            titleView.setText(photo.getTitle());
            photoImage.setScaleType(ScaleType.CENTER_CROP);

            Drawable drawable = photoImage.getDrawable();
            String smallUrl = photo.getSmallUrl();
            if (drawable != null && drawable instanceof DownloadedDrawable) {
                ImageDownloadTask task = ((DownloadedDrawable) drawable)
                        .getBitmapDownloaderTask();
                if (!smallUrl.equals(task.getUrl())) {
                    task.cancel(true);
                }
            }

            if (smallUrl == null) {
                File f = getSavedImageFile(photo.getId());
                if (f == null) {
                    photoImage.setImageDrawable(null);
                } else {
                    try {
                        photoImage.setImageBitmap(BitmapFactory
                                .decodeStream(new FileInputStream(f)));
                    } catch (FileNotFoundException e) {
                        photoImage.setImageDrawable(null);
                    }
                }
            } else {
                Bitmap cacheBitmap = ImageCache.getFromCache(smallUrl);
                if (cacheBitmap != null) {
                    photoImage.setImageBitmap(cacheBitmap);
                } else {
                    File f = getSavedImageFile(photo.getId());
                    if (f == null) {
                        ImageDownloadTask task = new ImageDownloadTask(
                                photoImage);
                        drawable = new DownloadedDrawable(task);
                        photoImage.setImageDrawable(drawable);
                        task.execute(smallUrl);
                    } else {
                        try {
                            photoImage.setImageBitmap(BitmapFactory
                                    .decodeStream(new FileInputStream(f)));
                        } catch (FileNotFoundException e) {
                            photoImage.setImageDrawable(null);
                        }
                    }
                }
            }

            return view;
        }

        private static class ViewHolder {
            ImageView image;
            TextView titleView;
        }

        private File getSavedImageFile(String photoId) {
            File root = new File(Environment.getExternalStorageDirectory(),
                    Constants.SD_CARD_FOLDER_NAME);
            File imageFile = new File(root, photoId + ".jpg"); //$NON-NLS-1$
            if (imageFile.exists()) {
                return imageFile;
            } else {
                return null;
            }
        }

    }

    @Override
    public void onItemClick(AdapterView<?> parentView, View view, int position,
            long id) {
        mSelectedPhoto = (Photo) mGridAdapter.getItem(position);
        GetPhotoDetailAction action = new GetPhotoDetailAction(getActivity(),
                mSelectedPhoto.getId());
        action.execute();

    }

    @SuppressWarnings("unchecked")
    @Override
    public void onPhotoListReady(PhotoList list, boolean cancelled) {
        if (list == null || list.isEmpty() || cancelled) {
            mCurrentPageNumber = mOldPageNumber;
            return;
        }

        mPhotoList.clear();
        for (int i = 0; i < list.size(); i++) {
            mPhotoList.add(list.get(i));
        }
        mGridAdapter.notifyDataSetChanged();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(BUNDLE_ATTR_DATA_PROVIDER,
                mPhotoListDataProvider);
        Log.d(TAG, "data provider is saved."); //$NON-NLS-1$
    }

    @Override
    public void handleMessage(FlickrViewerMessage message) {
        if (message != null) {
            String id = message.getMessageId();
            if (FlickrViewerMessage.FAV_PHOTO_REMOVED.equals(id)) {
                runPhotoListTask();
            }
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        FlickrViewerApplication app = (FlickrViewerApplication) activity.getApplication();
        app.registerMessageHandler(this);
    }

    @Override
    public void onDetach() {
        FlickrViewerApplication app = (FlickrViewerApplication) getActivity().getApplication();
        app.unregisterMessageHandler(this);
        super.onDetach();
    }

}
