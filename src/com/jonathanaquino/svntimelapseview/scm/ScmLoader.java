package com.jonathanaquino.svntimelapseview.scm;

import java.util.List;

import com.jonathanaquino.svntimelapseview.Closure;
import com.jonathanaquino.svntimelapseview.helpers.MiscHelper;

public abstract class ScmLoader {	
	private volatile boolean loading = false;
	private volatile boolean cancelled = false;
	
	private volatile String username;
	private volatile String password;
	
	protected void setLoading(boolean loading) {
		this.loading = loading;
	}
	
	public String KEY()
	{
		return ScmFactory.KEY(getClass());
	}
	
	protected void cancelHook()
	{
	}
	
	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * Builds a list of revisions for the given file, using a thread.
	 *
	 * @param filePathOrUrl  Subversion URL or working-copy file path
	 * @param username  username, or null for anonymous
	 * @param password  password, or null for anonymous
	 * @param limit  maximum number of revisions to download
	 * @param afterLoad  operation to run after the load finishes
	 */
	public void loadRevisions(final String filePathOrUrl, final int limit, final Closure afterLoad) throws Exception {
		loading = true;
		cancelled = false;
		Thread thread = new Thread(new Runnable() {
			public void run() {
				MiscHelper.handleExceptions(new Closure() {
					public void execute() throws Exception {
						try{
							loadRevisionsProper(filePathOrUrl, limit);
							afterLoad.execute();
						} finally {
						}
					}
				});
			}
		});
		thread.start();
	}

	/**
	 * Builds a list of revisions for the given file.
	 *
	 * @param filePathOrUrl  Subversion URL or working-copy file path
	 * @param username  username, or null for anonymous
	 * @param password  password, or null for anonymous
	 * @param limit  maximum number of revisions to download
	 * @param afterLoad  operation to run after the load finishes
	 */
	protected abstract void loadRevisionsProper(String filePathOrUrl, int limit) throws Exception;

	/**
	 * Tries to determine the character encoding of the given byte array.
	 * 
	 * @param array  the bytes of the string to analyze
	 * @return  the encoding (e.g., UTF-8) or null if it could not be determined.
	 */
	protected String determineEncoding(byte[] array) {
		if (array.length <= 2) { return null; }
		if (array[0] == (byte)0xFF && array[1] == (byte)0xFE) { return "UTF-16"; }
		if (array[0] == (byte)0xFE && array[1] == (byte)0xFF) { return "UTF-16"; }
		if (array[0] == (byte)0xEF && array[1] == (byte)0xBB) { return "UTF-8"; }
		return null;
	}

	/**
	 * Returns whether revisions are currently being downloaded.
	 *
	 * @return  whether the SvnLoader is loading revisions
	 */
	public boolean isLoading() {
		return loading;
	}

	/**
	 * Returns the number of revisions downloaded so far.
	 *
	 * @return  the number of revisions loaded in the current job.
	 */
	public abstract int getLoadedCount();

	/**
	 * Returns the total number of revisions that the current job is downloading.
	 *
	 * @return  the number of revisions being downloaded.
	 */
	public abstract int getTotalCount();

	/**
	 * Returns the Revisions for the file being examined.
	 *
	 * @return  the file's revision history
	 */
	public abstract List getRevisions();

	/**
	 * Requests that the load be cancelled.
	 */
	public void cancel() {
		if (!cancelled) {
			cancelHook();
			cancelled = true;
		}
	}
	
	protected boolean isCancelled() {
		return cancelled;
	}
}
