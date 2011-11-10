package com.jonathanaquino.svntimelapseview;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.jonathanaquino.svntimelapseview.helpers.GuiHelper;
import com.jonathanaquino.svntimelapseview.helpers.MiscHelper;

/**
 * The bar at the bottom of the application window.
 */
public class SearchPanel extends JPanel {

    /** The current diff being viewed. */
    private Diff currentDiff;

    /** Searches the text of the two revisions. */
    private Searcher searcher;

    /** The label displaying the number of differences in the current diff. */
    private JLabel differenceCountLabel = new JLabel();
    
    /** Checkbox for toggling between showing the entire file and showing differences only. */
    private JCheckBox showDifferencesOnlyCheckbox = new JCheckBox("Show differences only");
    
    private final ApplicationWindow applicationWindow;
    
    private Runnable previousDiff = new Runnable() {
		public void run() {
	    	MiscHelper.handleExceptions(new Closure() {
	            public void execute() throws Exception {
	                if (currentDiff == null || currentDiff.getDifferencePositions().size() == 0) { return; }
                    int nextPosition = previousDiffPosition();
                    if (nextPosition != -1) scrollToPosition(nextPosition);
	            }
	        });
		}
	};
    
    private Runnable nextDiff = new Runnable() {
		public void run() {
			MiscHelper.handleExceptions(new Closure() {
                public void execute() throws Exception {
                    if (currentDiff == null || currentDiff.getDifferencePositions().size() == 0) { return; }
                    int nextPosition = nextDiffPosition();
                    if (nextPosition != -1) scrollToPosition(nextPosition);
                };
            });
		}
	}; 
    
    /**
     * Creates a new SearchPanel.
     *
     * @param applicationWindow  the main window of the program
     */
    public SearchPanel(final ApplicationWindow applicationWindow) {
    	this.applicationWindow = applicationWindow;
    	
        setLayout(new GridBagLayout());
        final JTextField searchTextField = new JTextField(20);
        JButton searchButton = new JButton("Search");
        add(GuiHelper.pressOnEnterKey(searchTextField, searchButton), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        add(searchButton, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        searchButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                MiscHelper.handleExceptions(new Closure() {
                    public void execute() throws Exception {
                        if (searcher != null && searcher.search(searchTextField.getText().trim())) {
                            applicationWindow.highlight(searcher.getSide(), searcher.getPosition(), searchTextField.getText().trim().length());
                        }
                    }
                });
            }}
        );
        showDifferencesOnlyCheckbox.setSelected(applicationWindow.getApplication().getConfiguration().getBoolean("showDifferencesOnly", true));
        showDifferencesOnlyCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                MiscHelper.handleExceptions(new Closure() {
                    public void execute() throws Exception {
                        applicationWindow.getApplication().getConfiguration().setBoolean("showDifferencesOnly", isShowingDifferencesOnly());
                        applicationWindow.loadRevision();
                        if (!isShowingDifferencesOnly()) {
                        	gotoPreviousDiff();
                        }
                    }
                });
            }}
        );
        add(showDifferencesOnlyCheckbox, new GridBagConstraints(9, 0, 1, 1, 1.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 20), 0, 0));
        add(differenceCountLabel, new GridBagConstraints(10, 0, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 4), 0, 0));
        JButton previousButton = GuiHelper.setShortcutKey(new JButton("\u25B2"), KeyEvent.VK_UP, InputEvent.ALT_MASK);
        JButton nextButton = GuiHelper.setShortcutKey(new JButton("\u25BC"), KeyEvent.VK_DOWN, InputEvent.ALT_MASK);
        previousButton.setMargin(new Insets(0, 4, 0, 4));
        nextButton.setMargin(new Insets(0, 4, 0, 4));
        previousButton.setToolTipText("Previous Difference (Alt+\u21E7)");
        nextButton.setToolTipText("Next Difference (Alt+\u21E9)");
        previousButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previousDiff.run();
            }}
        );
        nextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                nextDiff.run();
            }}
        );
        add(previousButton, new GridBagConstraints(20, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        add(nextButton, new GridBagConstraints(30, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    }

    /**
     * Creates a new SearchPanel without parameters, for testing.
     */
    protected SearchPanel() {
    	this.applicationWindow = null;
    }

    /**
     * Sets the current diff being viewed.
     *
     * @param currentDiff  the set of differences between the current two text files
     */
    public void setCurrentDiff(Diff currentDiff, int currentPosition) {
        this.currentDiff = currentDiff;
        int n = currentDiff.getDifferencePositions().size();
        differenceCountLabel.setText(n + " difference" + (n == 1 ? "" : "s"));
        searcher = new Searcher(currentDiff.getLeftText(), currentDiff.getRightText());
    }
    
    public void gotoPreviousDiff() {
    	this.previousDiff.run();
    }
    
    public void gotoNextDiff() {
    	this.nextDiff.run();
    }

    /**
     * @return  the line position of the previous difference
     */
    public int previousDiffPosition(int linePosition, List<Integer> diffPositions) {
    	float prevPosition = linePosition - 0.5f;
    	for (int i = diffPositions.size() - 1; i >= 0; i--) {
    		int position = diffPositions.get(i);
    		if (position < prevPosition) {
    			return position;
    		}
    	}
    	// no diffs present before this line
    	return -1;
    }
    
    private int previousDiffPosition() {
    	return previousDiffPosition(applicationWindow.getScrollPosition(), currentDiff.getDifferencePositions());
    }

    /**
     * @return  the line position of the next difference
     */
    public int nextDiffPosition(int linePosition, List<Integer> diffPositions) {
    	float nextPosition = linePosition + 0.5f;
    	for (int i = 0; i < diffPositions.size(); i++) {
    		int position = diffPositions.get(i);
    		if (position > nextPosition) {
    			return position;
    		}
    	}
    	// no diffs present after this line
    	return -1;
    }
    
    private int nextDiffPosition() {
    	return nextDiffPosition(applicationWindow.getScrollPosition(), currentDiff.getDifferencePositions());
    }

    public int getNumLeftLines() { return currentDiff != null ? currentDiff.numLines() : 0; }
    
    /**
     * Scrolls the left and right editor panes to the current difference.
     * 
     * @param applicationWindow  the main window of the program
     */
    private void scrollToPosition(int position) {
        applicationWindow.scrollToLine(position);
    }

    /**
     * Whether the "Show differences only" checkbox is selected.
     * 
     * @return  whether to hide identical lines
     */
    public boolean isShowingDifferencesOnly() {
        return showDifferencesOnlyCheckbox.isSelected();
    }

}
