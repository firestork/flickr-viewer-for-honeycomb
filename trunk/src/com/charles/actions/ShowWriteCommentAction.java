/**
 * 
 */
package com.charles.actions;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;

import com.charles.ui.WriteCommentDialog;

/**
 * @author charles
 * 
 */
public class ShowWriteCommentAction extends ActivityAwareAction {

	private String mPhotoId;

	/**
	 * @param activity
	 */
	public ShowWriteCommentAction(Activity activity, String photoId) {
		super(activity);
		this.mPhotoId = photoId;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.charles.actions.IAction#execute()
	 */
	@Override
	public void execute() {
		FragmentManager fm = mActivity.getFragmentManager();
		FragmentTransaction ft = fm.beginTransaction();
		Fragment prev = fm.findFragmentByTag("write_comment_dlg");
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);

		// Create and show the dialog.
		WriteCommentDialog authDialog = new WriteCommentDialog(
				this.mPhotoId);
		authDialog.setCancelable(true);
		authDialog.show(ft, "write_comment_dlg");
	}

}
