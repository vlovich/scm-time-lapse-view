package com.jonathanaquino.svntimelapseview.scm;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevCommitList;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import com.jonathanaquino.svntimelapseview.Revision;

/**
 * Loads revisions from a subversion repository.
 */
public class GitLoader extends ScmLoader {
	public static final String KEY = "git";
	
	/** Number of revisions downloaded for the current file. */
	private volatile int loadedCount = 0;

	/** Total number of revisions to download for the current file. */
	private volatile int totalCount = 0;

	/** The list of Revisions being downloaded. */
	private List<GitRevision> revisions;

	private static DateFormat dateFormat = DateFormat.getDateInstance();
	
	private RevWalk walker;

	static class GitRevision extends Revision {
		Repository repository;
		RevCommit commit;
		AnyObjectId contentsId;

		public GitRevision(Repository repository, RevCommit commit, AnyObjectId contents) {
			this.repository = repository;
			this.commit = commit;
			this.contentsId = contents;
		}

		/**
		 * Returns the number identifying this revision.
		 * 
		 * @return the value from Subversion
		 */
		public Object getRevisionNumber() {
			return this.commit.getId();
		}

		/**
		 * Returns the username of the person who submitted this revision.
		 * 
		 * @return the value from Subversion
		 */
		public String getAuthor() {
			return this.commit.getCommitterIdent().toExternalString();
		}

		/**
		 * Returns the date on which the revision was submitted.
		 * 
		 * @return the value from Subversion
		 */
		public String getDate() {
			Calendar c = Calendar.getInstance();
			c.setTimeInMillis(this.commit.getCommitTime() * 1000);
			return dateFormat.format(c.getTime());
		}

		/**
		 * Returns the log message accompanying the submission of this revision.
		 * 
		 * @return the value from Subversion
		 */
		public String getLogMessage() {
			return this.commit.getFullMessage();
		}

		/**
		 * Returns the contents of the file that was submitted.
		 */
		public String getContents() {
			ObjectLoader loader;
			try {
				loader = this.repository.open(this.contentsId);
			} catch (Exception e) {
				return null;
			}
			
			byte[] contents = loader.getCachedBytes();
			return new String(contents, Constants.CHARSET);
		}
	}

	/**
	 * Builds a list of revisions for the given file.
	 * 
	 * @param filePath
	 *            Git workdir
	 * @param username
	 *            username, or null for anonymous
	 * @param password
	 *            password, or null for anonymous
	 * @param limit
	 *            maximum number of revisions to download
	 * @param afterLoad
	 *            operation to run after the load finishes
	 */
	protected void loadRevisionsProper(String filePath, int limit)
			throws Exception {
		try {
			loadedCount = totalCount = 1;
			FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
			if (filePath.startsWith("~/")) {
				String home = System.getenv("HOME");
				if (home.isEmpty()) home = System.getenv("USERPROFILE");
				filePath = filePath.replaceFirst("^~", home);
			}
			File target = new File(filePath).getAbsoluteFile();
			Repository repository = repositoryBuilder
					.readEnvironment()
					.findGitDir(target)
					.build();

			File workTree = repository.getWorkTree();

			String repositoryPath = Repository.stripWorkDir(workTree, target);
			if (repositoryPath.isEmpty()) {
				if (revisions == null)
					revisions = new ArrayList();
				System.err.println("Cannot time-lapse view directory");
				setLoading(false);
				return;
			}

			ObjectId HEAD = repository.resolve(Constants.HEAD);

			if (walker != null) {
				walker.dispose();
			}
			
			walker = new RevWalk(repository);
			RevCommit startCommit = walker.parseCommit(HEAD);
			
			List<String> filterPaths = new ArrayList<String>(1);
			filterPaths.add(repositoryPath);

			walker.markStart(startCommit);
			TreeFilter filter = AndTreeFilter.create(new TreeFilter[] {
					TreeFilter.ANY_DIFF,
					FollowFilter.create(repositoryPath),
					PathFilter.create(repositoryPath)
			});
			walker.setTreeFilter(filter);

			RevCommitList<RevCommit> commitList = new RevCommitList<RevCommit>();
			commitList.source(walker);
			commitList.fillTo(limit);
			
			totalCount = commitList.size();
			
			revisions = new LinkedList<GitRevision>();
			
			for (RevCommit commit : commitList) {
				TreeWalk treeWalker = TreeWalk.forPath(repository, repositoryPath, commit.getTree());
				if (treeWalker == null) continue;
				
				treeWalker.setRecursive(true);
				CanonicalTreeParser parser = treeWalker.getTree(0, CanonicalTreeParser.class);
				
				while (!parser.eof()) {
					if (repositoryPath.equals(parser.getEntryPathString())) {
						GitRevision rev = new GitRevision(repository, commit, parser.getEntryObjectId());
						revisions.add(0, rev);
					}
					
					parser.next();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		} finally {
			setLoading(false);
		}
	}

	/**
	 * Returns the number of revisions downloaded so far.
	 * 
	 * @return the number of revisions loaded in the current job.
	 */
	public int getLoadedCount() {
		return loadedCount;
	}

	/**
	 * Returns the total number of revisions that the current job is
	 * downloading.
	 * 
	 * @return the number of revisions being downloaded.
	 */
	public int getTotalCount() {
		return 0;
	}

	/**
	 * Returns the Revisions for the file being examined.
	 * 
	 * @return the file's revision history
	 */
	public List getRevisions() {
		return revisions;
	}
}
