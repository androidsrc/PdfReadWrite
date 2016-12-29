package com.androidsrc.pdfdemo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfDocument.Page;
import android.graphics.pdf.PdfDocument.PageInfo;
import android.graphics.pdf.PdfRenderer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {

	/**
	 * For identifying current view mode read/create/listing/options
	 * @author androidsrc.net
	 *
	 */
	interface CurrentView {
		int OPTIONS_LAYOUT = 1;
		int READ_LAYOUT = 2;
		int WRITE_LAYOUT = 3;
		int PDF_SELECTION_LAYOUT = 4;
	}

	/**
	 * FrameLayout child views. We will manage our UI to one layout 
	 * Hide/Show these views as per requirement
	 */
	LinearLayout optionsLayout;
	LinearLayout readLayout;
	LinearLayout writeLayout;
	LinearLayout pdfSelectionLayout;

	private static int currentView;

	// Pdf content will be generated with User Input Text
	EditText pdfContentView;
	//For navigating back
	MenuItem closeOption;
	// List view for showing pdf files
	ListView pdfList;
	//Background task to generate pdf file listing
	PdfListLoadTask listTask;
	//Adapter to list view
	ArrayAdapter<String> adapter;
	// array of pdf files 
	File[] filelist;

	//index to track currentPage in rendered Pdf
	private static int currentPage = 0;
	//View on which Pdf content will be rendered
	ImageView pdfView;

	//Currently rendered Pdf file
	String openedPdfFileName;
	Button generatePdf;
	Button next;
	Button previous;

	//File Descriptor for rendered Pdf file
	private ParcelFileDescriptor mFileDescriptor;
	//For rendering a PDF document
	private PdfRenderer mPdfRenderer;
	//For opening current page, render it, and close the page
	private PdfRenderer.Page mCurrentPage;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		optionsLayout = (LinearLayout) findViewById(R.id.options_layout);
		readLayout = (LinearLayout) findViewById(R.id.read_layout);
		writeLayout = (LinearLayout) findViewById(R.id.write_layout);
		pdfSelectionLayout = (LinearLayout) findViewById(R.id.pdf_selection_layout);
		pdfContentView = (EditText) findViewById(R.id.pdf_content);

		findViewById(R.id.read_pdf).setOnClickListener(this);
		findViewById(R.id.create_new_pdf).setOnClickListener(this);
		next = (Button) findViewById(R.id.next);
		next.setOnClickListener(this);
		previous = (Button) findViewById(R.id.previous);
		previous.setOnClickListener(this);
		generatePdf = (Button) findViewById(R.id.generate_pdf);
		generatePdf.setOnClickListener(this);

		pdfList = (ListView) findViewById(R.id.pdfList);
		pdfList.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				//On Clicking list item, Render Pdf file corresponding to filePath
				openedPdfFileName = adapter.getItem(position);
				openRenderer(openedPdfFileName);
				updateView(CurrentView.READ_LAYOUT);
			}
		});
		pdfView = (ImageView) findViewById(R.id.pdfView);

		currentView = CurrentView.OPTIONS_LAYOUT;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		closeOption = menu.findItem(R.id.action_close);
		closeOption.setVisible(false);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_close) {
			if (currentView == CurrentView.PDF_SELECTION_LAYOUT) {
				updateView(CurrentView.OPTIONS_LAYOUT);
				updateActionBarText();
			} else if (currentView == CurrentView.READ_LAYOUT) {
				if (listTask != null)
					listTask.cancel(true);
				listTask = new PdfListLoadTask();
				listTask.execute();
				updateView(CurrentView.PDF_SELECTION_LAYOUT);
			} else if (currentView == CurrentView.WRITE_LAYOUT) {
				hideInputMethodIfShown();
				updateView(CurrentView.OPTIONS_LAYOUT);
			}
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	
	/**
	 * Handler back key 
	 * Update UI current view is not options view
	 * else call super.onBackPressed()
	 */
	@Override
	public void onBackPressed() {
			if (currentView == CurrentView.PDF_SELECTION_LAYOUT) {
				updateView(CurrentView.OPTIONS_LAYOUT);
				updateActionBarText();
			} else if (currentView == CurrentView.READ_LAYOUT) {
				if (listTask != null)
					listTask.cancel(true);
				listTask = new PdfListLoadTask();
				listTask.execute();
				updateView(CurrentView.PDF_SELECTION_LAYOUT);
			} else if (currentView == CurrentView.WRITE_LAYOUT) {
				hideInputMethodIfShown();
				updateView(CurrentView.OPTIONS_LAYOUT);
			}else{
				super.onBackPressed();
			}
	}

	/**
	 * API to hide keyboard if shown
	 * Will be used when user naviagates after generating Pdf
	 */
	private void hideInputMethodIfShown() {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(pdfContentView.getWindowToken(), 0, null);
	}

	
	/**
	 * API to update ActionBar text
	 */
	private void updateActionBarText() {
		if (currentView == CurrentView.READ_LAYOUT) {
			int index = mCurrentPage.getIndex();
			int pageCount = mPdfRenderer.getPageCount();
			previous.setEnabled(0 != index);
			next.setEnabled(index + 1 < pageCount);
			getActionBar().setTitle(
					openedPdfFileName + "(" + (index + 1) + "/" + pageCount
							+ ")");
		} else {
			getActionBar().setTitle(R.string.app_name);
		}
	}

	/**
	 * API to update View
	 * @param updateView
	 * updateView specifies the target view
	 */
	private void updateView(int updateView) {
		switch (updateView) {
		case CurrentView.OPTIONS_LAYOUT:
			currentView = CurrentView.OPTIONS_LAYOUT;
			closeOption.setVisible(false);
			optionsLayout.setVisibility(View.VISIBLE);
			readLayout.setVisibility(View.INVISIBLE);
			writeLayout.setVisibility(View.INVISIBLE);
			pdfSelectionLayout.setVisibility(View.INVISIBLE);
			break;
		case CurrentView.READ_LAYOUT:
			currentView = CurrentView.READ_LAYOUT;
			showPage(currentPage);

			closeOption.setVisible(true);
			optionsLayout.setVisibility(View.INVISIBLE);
			readLayout.setVisibility(View.VISIBLE);
			writeLayout.setVisibility(View.INVISIBLE);
			pdfSelectionLayout.setVisibility(View.INVISIBLE);
			break;
		case CurrentView.PDF_SELECTION_LAYOUT:
			currentView = CurrentView.PDF_SELECTION_LAYOUT;

			closeRenderer();

			if (listTask != null)
				listTask.cancel(true);
			listTask = new PdfListLoadTask();
			listTask.execute();

			closeOption.setVisible(true);
			optionsLayout.setVisibility(View.INVISIBLE);
			readLayout.setVisibility(View.INVISIBLE);
			writeLayout.setVisibility(View.INVISIBLE);
			pdfSelectionLayout.setVisibility(View.VISIBLE);
			break;
		case CurrentView.WRITE_LAYOUT:
			currentView = CurrentView.WRITE_LAYOUT;

			closeOption.setVisible(true);
			optionsLayout.setVisibility(View.INVISIBLE);
			readLayout.setVisibility(View.INVISIBLE);
			writeLayout.setVisibility(View.VISIBLE);
			pdfSelectionLayout.setVisibility(View.INVISIBLE);
			break;
		}
	}

	
	/**
	 * Callback for handling view click events
	 */
	@Override
	public void onClick(View v) {
		int viewId = v.getId();
		switch (viewId) {
		case R.id.read_pdf:
			updateView(CurrentView.PDF_SELECTION_LAYOUT);
			break;
		case R.id.create_new_pdf:
			updateView(CurrentView.WRITE_LAYOUT);
			break;
		case R.id.generate_pdf:
			if (pdfContentView.getText().toString().isEmpty()) {
				Toast.makeText(getApplicationContext(),
						"Please enter text to generate Pdf", Toast.LENGTH_SHORT)
						.show();
			} else {
				new PdfGenerationTask().execute();
				v.setEnabled(false);
			}
			break;
		case R.id.next:
			currentPage++;
			showPage(currentPage);
			break;
		case R.id.previous:
			currentPage--;
			showPage(currentPage);
			break;
		}

	}

	/**
	 * API for initializing file descriptor and pdf renderer after selecting pdf from list 
	 * @param filePath
	 */
	private void openRenderer(String filePath) {
		File file = new File(filePath);
		try {
			mFileDescriptor = ParcelFileDescriptor.open(file,
					ParcelFileDescriptor.MODE_READ_ONLY);
			mPdfRenderer = new PdfRenderer(mFileDescriptor);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * API for cleanup of objects used in rendering 
	 */
	private void closeRenderer() {

		try {
			if (mCurrentPage != null)
				mCurrentPage.close();
			if (mPdfRenderer != null)
				mPdfRenderer.close();
			if (mFileDescriptor != null)
				mFileDescriptor.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * API show to particular page index using PdfRenderer 
	 * @param index
	 */
	private void showPage(int index) {
		if (mPdfRenderer == null || mPdfRenderer.getPageCount() <= index
				|| index < 0) {
			return;
		}
		// For closing the current page before opening another one.
		try {
			if (mCurrentPage != null) {
				mCurrentPage.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// Open page with specified index
		mCurrentPage = mPdfRenderer.openPage(index);
		Bitmap bitmap = Bitmap.createBitmap(mCurrentPage.getWidth(),
				mCurrentPage.getHeight(), Bitmap.Config.ARGB_8888);
		
		//Pdf page is rendered on Bitmap
		mCurrentPage.render(bitmap, null, null,
				PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
		//Set rendered bitmap to ImageView
		pdfView.setImageBitmap(bitmap);
		updateActionBarText();
	}

	/**
	 * Background task for listing pdf files
	 * @author androidsrc.net
	 *
	 */
	private class PdfListLoadTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			File files = new File("/sdcard/PDFDemo_AndroidSRC/");
			filelist = files.listFiles(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return ((name.endsWith(".pdf")));
				}
			});

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			// TODO Auto-generated method stub

			if (filelist != null && filelist.length >= 1) {
				ArrayList<String> fileNameList = new ArrayList<>();
				for (int i = 0; i < filelist.length; i++)
					fileNameList.add(filelist[i].getPath());
				adapter = new ArrayAdapter<>(getApplicationContext(),
						R.layout.list_item, fileNameList);
				pdfList.setAdapter(adapter);
			} else {
				Toast.makeText(getApplicationContext(),
						"No pdf file found, Please create new Pdf file",
						Toast.LENGTH_LONG).show();
				updateView(CurrentView.OPTIONS_LAYOUT);
				updateActionBarText();
			}
		}

	}

	/**
	 * Background task to generate pdf from users content
	 * @author androidsrc.net
	 *
	 */
	private class PdfGenerationTask extends AsyncTask<Void, Void, String> {

		@Override
		protected String doInBackground(Void... params) {

			PdfDocument document = new PdfDocument();

			// repaint the user's text into the page
			View content = findViewById(R.id.pdf_content);

			// crate a page description
			int pageNumber = 1;
			PageInfo pageInfo = new PageInfo.Builder(content.getWidth(),
					content.getHeight() - 20, pageNumber).create();

			// create a new page from the PageInfo
			Page page = document.startPage(pageInfo);

			content.draw(page.getCanvas());

			// do final processing of the page
			document.finishPage(page);

			SimpleDateFormat sdf = new SimpleDateFormat("ddMMyyyyhhmmss");
			String pdfName = "pdfdemo"
					+ sdf.format(Calendar.getInstance().getTime()) + ".pdf";

			File outputFile = new File("/sdcard/PDFDemo_AndroidSRC/", pdfName);

			try {
				outputFile.createNewFile();
				OutputStream out = new FileOutputStream(outputFile);
				document.writeTo(out);
				document.close();
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return outputFile.getPath();
		}

		@Override
		protected void onPostExecute(String filePath) {
			if (filePath != null) {
				generatePdf.setEnabled(true);
				pdfContentView.setText("");
				Toast.makeText(getApplicationContext(),
						"Pdf saved at " + filePath, Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(getApplicationContext(),
						"Error in Pdf creation" + filePath, Toast.LENGTH_SHORT)
						.show();
			}

		}

	}
}
