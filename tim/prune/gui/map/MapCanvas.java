package tim.prune.gui.map;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import tim.prune.App;
import tim.prune.DataSubscriber;
import tim.prune.FunctionLibrary;
import tim.prune.I18nManager;
import tim.prune.UpdateMessageBroker;
import tim.prune.config.ColourScheme;
import tim.prune.config.Config;
import tim.prune.data.Checker;
import tim.prune.data.Coordinate;
import tim.prune.data.DataPoint;
import tim.prune.data.DoubleRange;
import tim.prune.data.Field;
import tim.prune.data.FieldList;
import tim.prune.data.Latitude;
import tim.prune.data.Longitude;
import tim.prune.data.MidpointData;
import tim.prune.data.Selection;
import tim.prune.data.Track;
import tim.prune.data.TrackInfo;
import tim.prune.function.compress.MarkPointsInRectangleFunction;
import tim.prune.function.edit.FieldEdit;
import tim.prune.function.edit.FieldEditList;
import tim.prune.gui.IconManager;

/**
 * Class for the map canvas, to display a background map and draw on it
 */
public class MapCanvas extends JPanel implements MouseListener, MouseMotionListener, DataSubscriber,
	KeyListener, MouseWheelListener
{
	/** App object for callbacks */
	private App _app = null;
	/** Track object */
	private Track _track = null;
	/** TrackInfo object */
	private TrackInfo _trackInfo = null;
	/** Selection object */
	private Selection _selection = null;
	/** Object to keep track of midpoints */
	private MidpointData _midpoints = null;
	/** Index of point clicked at mouseDown */
	private int _clickedPoint = -1;
	/** Previously selected point */
	private int _prevSelectedPoint = -1;
	/** Tile manager */
	private MapTileManager _tileManager = new MapTileManager(this);
	/** Image to display */
	private BufferedImage _mapImage = null;
	/** Slider for transparency */
	private JSlider _transparencySlider = null;
	/** Checkbox for scale bar */
	private JCheckBox _scaleCheckBox = null;
	/** Checkbox for maps */
	private JCheckBox _mapCheckBox = null;
	/** Checkbox for autopan */
	private JCheckBox _autopanCheckBox = null;
	/** Checkbox for connecting track points */
	private JCheckBox _connectCheckBox = null;
	/** Checkbox for enable edit mode */
	private JCheckBox _editmodeCheckBox = null;
	/** Right-click popup menu */
	private JPopupMenu _popup = null;
	/** Top component panel */
	private JPanel _topPanel = null;
	/** Side component panel */
	private JPanel _sidePanel = null;
	/** Scale bar */
	private ScaleBar _scaleBar = null;
	/* Data */
	private DoubleRange _latRange = null, _lonRange = null;
	private DoubleRange _xRange = null, _yRange = null;
	private boolean _recalculate = false;
	/** Flag to check bounds on next paint */
	private boolean _checkBounds = false;
	/** Map position */
	private MapPosition _mapPosition = null;
	/** coordinates of drag from point */
	private int _dragFromX = -1, _dragFromY = -1;
	/** coordinates of drag to point */
	private int _dragToX = -1, _dragToY = -1;
	/** coordinates of popup menu */
	private int _popupMenuX = -1, _popupMenuY = -1;
	/** Flag to prevent showing too often the error message about loading maps */
	private boolean _shownOsmErrorAlready = false;
	/** Current drawing mode */
	private int _drawMode = MODE_DEFAULT;

	/** Constant for click sensitivity when selecting nearest point */
	private static final int CLICK_SENSITIVITY = 10;
	/** Constant for pan distance from key presses */
	private static final int PAN_DISTANCE = 20;
	/** Constant for pan distance from autopan */
	private static final int AUTOPAN_DISTANCE = 75;

	// Colours
	private static final Color COLOR_MESSAGES   = Color.GRAY;

	// Drawing modes
	private static final int MODE_DEFAULT = 0;
	private static final int MODE_ZOOM_RECT = 1;
	private static final int MODE_DRAW_POINTS_START = 2;
	private static final int MODE_DRAW_POINTS_CONT = 3;
	private static final int MODE_DRAG_POINT = 4;
	private static final int MODE_CREATE_MIDPOINT = 5;
	private static final int MODE_MARK_RECTANGLE = 6;

	private static final int INDEX_UNKNOWN  = -2;


	/**
	 * Constructor
	 * @param inApp App object for callbacks
	 * @param inTrackInfo track info object
	 */
	public MapCanvas(App inApp, TrackInfo inTrackInfo)
	{
		_app = inApp;
		_trackInfo = inTrackInfo;
		_track = inTrackInfo.getTrack();
		_selection = inTrackInfo.getSelection();
		_midpoints = new MidpointData();
		_mapPosition = new MapPosition();
		addMouseListener(this);
		addMouseMotionListener(this);
		addMouseWheelListener(this);
		addKeyListener(this);

		// Make listener for changes to controls
		ItemListener itemListener = new ItemListener() {
			public void itemStateChanged(ItemEvent e)
			{
				_recalculate = true;
				repaint();
			}
		};
		// Make special listener for changes to map checkbox
		ItemListener mapCheckListener = new ItemListener() {
			public void itemStateChanged(ItemEvent e)
			{
				_tileManager.clearMemoryCaches();
				_recalculate = true;
				Config.setConfigBoolean(Config.KEY_SHOW_MAP, e.getStateChange() == ItemEvent.SELECTED);
				UpdateMessageBroker.informSubscribers(); // to let menu know
				// If the track is only partially visible and you turn the map off, make the track fully visible again
				if (e.getStateChange() == ItemEvent.DESELECTED && _transparencySlider.getValue() < 0) {
					_transparencySlider.setValue(0);
				}
			}
		};
		_topPanel = new OverlayPanel();
		_topPanel.setLayout(new FlowLayout());
		// Make slider for transparency
		_transparencySlider = new JSlider(-6, 6, 0);
		_transparencySlider.setPreferredSize(new Dimension(100, 20));
		_transparencySlider.setMajorTickSpacing(1);
		_transparencySlider.setSnapToTicks(true);
		_transparencySlider.setOpaque(false);
		_transparencySlider.setValue(0);
		_transparencySlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e)
			{
				int val = _transparencySlider.getValue();
				if (val == 1 || val == -1)
					_transparencySlider.setValue(0);
				else {
					_recalculate = true;
					repaint();
				}
			}
		});
		_transparencySlider.setFocusable(false); // stop slider from stealing keyboard focus
		_topPanel.add(_transparencySlider);
		// Add checkbox button for enabling scale bar
		_scaleCheckBox = new JCheckBox(IconManager.getImageIcon(IconManager.SCALEBAR_BUTTON), true);
		_scaleCheckBox.setSelectedIcon(IconManager.getImageIcon(IconManager.SCALEBAR_BUTTON_ON));
		_scaleCheckBox.setOpaque(false);
		_scaleCheckBox.setToolTipText(I18nManager.getText("menu.map.showscalebar"));
		_scaleCheckBox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				_scaleBar.setVisible(_scaleCheckBox.isSelected());
			}
		});
		_scaleCheckBox.setFocusable(false); // stop button from stealing keyboard focus
		_topPanel.add(_scaleCheckBox);
		// Add checkbox button for enabling maps or not
		_mapCheckBox = new JCheckBox(IconManager.getImageIcon(IconManager.MAP_BUTTON), false);
		_mapCheckBox.setSelectedIcon(IconManager.getImageIcon(IconManager.MAP_BUTTON_ON));
		_mapCheckBox.setOpaque(false);
		_mapCheckBox.setToolTipText(I18nManager.getText("menu.map.showmap"));
		_mapCheckBox.addItemListener(mapCheckListener);
		_mapCheckBox.setFocusable(false); // stop button from stealing keyboard focus
		_topPanel.add(_mapCheckBox);
		// Add checkbox button for enabling autopan or not
		_autopanCheckBox = new JCheckBox(IconManager.getImageIcon(IconManager.AUTOPAN_BUTTON), true);
		_autopanCheckBox.setSelectedIcon(IconManager.getImageIcon(IconManager.AUTOPAN_BUTTON_ON));
		_autopanCheckBox.setOpaque(false);
		_autopanCheckBox.setToolTipText(I18nManager.getText("menu.map.autopan"));
		_autopanCheckBox.addItemListener(itemListener);
		_autopanCheckBox.setFocusable(false); // stop button from stealing keyboard focus
		_topPanel.add(_autopanCheckBox);
		// Add checkbox button for connecting points or not
		_connectCheckBox = new JCheckBox(IconManager.getImageIcon(IconManager.POINTS_DISCONNECTED_BUTTON), true);
		_connectCheckBox.setSelectedIcon(IconManager.getImageIcon(IconManager.POINTS_CONNECTED_BUTTON));
		_connectCheckBox.setOpaque(false);
		_connectCheckBox.setToolTipText(I18nManager.getText("menu.map.connect"));
		_connectCheckBox.addItemListener(itemListener);
		_connectCheckBox.setFocusable(false); // stop button from stealing keyboard focus
		_topPanel.add(_connectCheckBox);

		// Add checkbox button for edit mode or not
		_editmodeCheckBox = new JCheckBox(IconManager.getImageIcon(IconManager.EDIT_MODE_BUTTON), false);
		_editmodeCheckBox.setSelectedIcon(IconManager.getImageIcon(IconManager.EDIT_MODE_BUTTON_ON));
		_editmodeCheckBox.setOpaque(false);
		_editmodeCheckBox.setToolTipText(I18nManager.getText("menu.map.editmode"));
		_editmodeCheckBox.addItemListener(itemListener);
		_editmodeCheckBox.setFocusable(false); // stop button from stealing keyboard focus
		_topPanel.add(_editmodeCheckBox);

		// Add zoom in, zoom out buttons
		_sidePanel = new OverlayPanel();
		_sidePanel.setLayout(new BoxLayout(_sidePanel, BoxLayout.Y_AXIS));
		JButton zoomInButton = new JButton(IconManager.getImageIcon(IconManager.ZOOM_IN_BUTTON));
		zoomInButton.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		zoomInButton.setContentAreaFilled(false);
		zoomInButton.setToolTipText(I18nManager.getText("menu.map.zoomin"));
		zoomInButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				zoomIn();
			}
		});
		zoomInButton.setFocusable(false); // stop button from stealing keyboard focus
		_sidePanel.add(zoomInButton);
		JButton zoomOutButton = new JButton(IconManager.getImageIcon(IconManager.ZOOM_OUT_BUTTON));
		zoomOutButton.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		zoomOutButton.setContentAreaFilled(false);
		zoomOutButton.setToolTipText(I18nManager.getText("menu.map.zoomout"));
		zoomOutButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				zoomOut();
			}
		});
		zoomOutButton.setFocusable(false); // stop button from stealing keyboard focus
		_sidePanel.add(zoomOutButton);

		// Bottom panel for scale bar
		_scaleBar = new ScaleBar();

		// add control panels to this one
		setLayout(new BorderLayout());
		_topPanel.setVisible(false);
		_sidePanel.setVisible(false);
		add(_topPanel, BorderLayout.NORTH);
		add(_sidePanel, BorderLayout.WEST);
		add(_scaleBar, BorderLayout.SOUTH);
		// Make popup menu
		makePopup();
	}


	/**
	 * Make the popup menu for right-clicking the map
	 */
	private void makePopup()
	{
		_popup = new JPopupMenu();
		JMenuItem zoomInItem = new JMenuItem(I18nManager.getText("menu.map.zoomin"));
		zoomInItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				panMap((_popupMenuX - getWidth()/2)/2, (_popupMenuY - getHeight()/2)/2);
				zoomIn();
			}});
		_popup.add(zoomInItem);
		JMenuItem zoomOutItem = new JMenuItem(I18nManager.getText("menu.map.zoomout"));
		zoomOutItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				panMap(-(_popupMenuX - getWidth()/2), -(_popupMenuY - getHeight()/2));
				zoomOut();
			}});
		_popup.add(zoomOutItem);
		JMenuItem zoomFullItem = new JMenuItem(I18nManager.getText("menu.map.zoomfull"));
		zoomFullItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				zoomToFit();
				_recalculate = true;
				repaint();
			}});
		_popup.add(zoomFullItem);
		_popup.addSeparator();
		// Set background
		JMenuItem setMapBgItem = new JMenuItem(
			I18nManager.getText(FunctionLibrary.FUNCTION_SET_MAP_BG.getNameKey()));
		setMapBgItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				FunctionLibrary.FUNCTION_SET_MAP_BG.begin();
			}});
		_popup.add(setMapBgItem);
		// new point option
		JMenuItem newPointItem = new JMenuItem(I18nManager.getText("menu.map.newpoint"));
		newPointItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				_app.createPoint(createPointFromClick(_popupMenuX, _popupMenuY));
			}});
		_popup.add(newPointItem);
		// draw point series
		JMenuItem drawPointsItem = new JMenuItem(I18nManager.getText("menu.map.drawpoints"));
		drawPointsItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				_drawMode = MODE_DRAW_POINTS_START;
			}
		});
		_popup.add(drawPointsItem);
	}


	/**
	 * Zoom to fit the current data area
	 */
	private void zoomToFit()
	{
		_latRange = _track.getLatRange();
		_lonRange = _track.getLonRange();
		_xRange = new DoubleRange(MapUtils.getXFromLongitude(_lonRange.getMinimum()),
			MapUtils.getXFromLongitude(_lonRange.getMaximum()));
		_yRange = new DoubleRange(MapUtils.getYFromLatitude(_latRange.getMinimum()),
			MapUtils.getYFromLatitude(_latRange.getMaximum()));
		_mapPosition.zoomToXY(_xRange.getMinimum(), _xRange.getMaximum(), _yRange.getMinimum(), _yRange.getMaximum(),
			getWidth(), getHeight());
	}


	/**
	 * Paint method
	 * @see java.awt.Canvas#paint(java.awt.Graphics)
	 */
	public void paint(Graphics inG)
	{
		super.paint(inG);
		if (_mapImage != null && (_mapImage.getWidth() != getWidth() || _mapImage.getHeight() != getHeight())) {
			_mapImage = null;
		}
		if (_track.getNumPoints() > 0)
		{
			// Check for autopan if enabled / necessary
			if (_autopanCheckBox.isSelected())
			{
				int selectedPoint = _selection.getCurrentPointIndex();
				if (selectedPoint >= 0 && _dragFromX == -1 && selectedPoint != _prevSelectedPoint)
				{
					int px = getWidth() / 2 + _mapPosition.getXFromCentre(_track.getX(selectedPoint));
					int py = getHeight() / 2 + _mapPosition.getYFromCentre(_track.getY(selectedPoint));
					int panX = 0;
					int panY = 0;
					if (px < PAN_DISTANCE) {
						panX = px - AUTOPAN_DISTANCE;
					}
					else if (px > (getWidth()-PAN_DISTANCE)) {
						panX = AUTOPAN_DISTANCE + px - getWidth();
					}
					if (py < PAN_DISTANCE) {
						panY = py - AUTOPAN_DISTANCE;
					}
					if (py > (getHeight()-PAN_DISTANCE)) {
						panY = AUTOPAN_DISTANCE + py - getHeight();
					}
					if (panX != 0 || panY != 0) {
						_mapPosition.pan(panX, panY);
					}
				}
				_prevSelectedPoint = selectedPoint;
			}

			// Draw the map contents if necessary
			if ((_mapImage == null || _recalculate))
			{
				paintMapContents();
				_scaleBar.updateScale(_mapPosition.getZoom(), _mapPosition.getYFromPixels(0, 0));
			}
			// Draw the prepared image onto the panel
			if (_mapImage != null) {
				inG.drawImage(_mapImage, 0, 0, getWidth(), getHeight(), null);
			}

			switch (_drawMode)
			{
				case MODE_DRAG_POINT:
					drawDragLines(inG, _selection.getCurrentPointIndex()-1, _selection.getCurrentPointIndex()+1);
					break;

				case MODE_CREATE_MIDPOINT:
					drawDragLines(inG, _clickedPoint-1, _clickedPoint);
					break;

				case MODE_ZOOM_RECT:
				case MODE_MARK_RECTANGLE:
					if (_dragFromX != -1 && _dragFromY != -1)
					{
						// Draw the zoom rectangle if necessary
						inG.setColor(Color.RED);
						inG.drawLine(_dragFromX, _dragFromY, _dragFromX, _dragToY);
						inG.drawLine(_dragFromX, _dragFromY, _dragToX, _dragFromY);
						inG.drawLine(_dragToX, _dragFromY, _dragToX, _dragToY);
						inG.drawLine(_dragFromX, _dragToY, _dragToX, _dragToY);
					}
					break;
					
				case MODE_DRAW_POINTS_CONT:
					// draw line to mouse position to show drawing mode
					inG.setColor(Config.getColourScheme().getColour(ColourScheme.IDX_POINT));
					int prevIndex = _track.getNumPoints()-1;
					int px = getWidth() / 2 + _mapPosition.getXFromCentre(_track.getX(prevIndex));
					int py = getHeight() / 2 + _mapPosition.getYFromCentre(_track.getY(prevIndex));
					inG.drawLine(px, py, _dragToX, _dragToY);
					break;
			}
		}
		else
		{
			inG.setColor(Config.getColourScheme().getColour(ColourScheme.IDX_BACKGROUND));
			inG.fillRect(0, 0, getWidth(), getHeight());
			inG.setColor(COLOR_MESSAGES);
			inG.drawString(I18nManager.getText("display.nodata"), 50, getHeight()/2);
			_scaleBar.updateScale(-1, 0);
		}
		// Draw slider etc on top
		paintChildren(inG);
	}


	/**
	 * Paint the map tiles and the points on to the _mapImage
	 */
	private void paintMapContents()
	{
		if (_mapImage == null || _mapImage.getWidth() != getWidth() || _mapImage.getHeight() != getHeight())
		{
			_mapImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
		}

		// Clear map
		Graphics g = _mapImage.getGraphics();
		// Clear to background
		g.setColor(Config.getColourScheme().getColour(ColourScheme.IDX_BACKGROUND));
		g.fillRect(0, 0, getWidth(), getHeight());

		// Check whether maps are on or not
		boolean showMap = Config.getConfigBoolean(Config.KEY_SHOW_MAP);
		_mapCheckBox.setSelected(showMap);

		// reset error message
		if (!showMap) {_shownOsmErrorAlready = false;}
		_recalculate = false;
		// Only get map tiles if selected
		if (showMap)
		{
			// init tile cacher
			_tileManager.centreMap(_mapPosition.getZoom(), _mapPosition.getCentreTileX(), _mapPosition.getCentreTileY());

			boolean loadingFailed = false;
			if (_mapImage == null) return;

			if (_tileManager.isOverzoomed())
			{
				// display overzoom message
				g.setColor(COLOR_MESSAGES);
				g.drawString(I18nManager.getText("map.overzoom"), 50, getHeight()/2);
			}
			else
			{
				int numLayers = _tileManager.getNumLayers();
				// Loop over tiles drawing each one
				int[] tileIndices = _mapPosition.getTileIndices(getWidth(), getHeight());
				int[] pixelOffsets = _mapPosition.getDisplayOffsets(getWidth(), getHeight());
				for (int tileX = tileIndices[0]; tileX <= tileIndices[1] && !loadingFailed; tileX++)
				{
					int x = (tileX - tileIndices[0]) * 256 - pixelOffsets[0];
					for (int tileY = tileIndices[2]; tileY <= tileIndices[3]; tileY++)
					{
						int y = (tileY - tileIndices[2]) * 256 - pixelOffsets[1];
						// Loop over layers
						for (int l=0; l<numLayers; l++)
						{
							Image image = _tileManager.getTile(l, tileX, tileY);
							if (image != null) {
								g.drawImage(image, x, y, 256, 256, null);
							}
						}
					}
				}

				// Make maps brighter / fainter according to slider
				final int brightnessIndex = Math.max(1, _transparencySlider.getValue()) - 1;
				if (brightnessIndex > 0)
				{
					final int[] alphas = {0, 40, 80, 120, 160, 210};
					Color bgColor = Config.getColourScheme().getColour(ColourScheme.IDX_BACKGROUND);
					bgColor = new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), alphas[brightnessIndex]);
					g.setColor(bgColor);
					g.fillRect(0, 0, getWidth(), getHeight());
				}
			}
		}

		// Paint the track points on top
		int pointsPainted = 1;
		try
		{
			pointsPainted = paintPoints(g);
		}
		catch (NullPointerException npe) { // ignore, probably due to data being changed during drawing
		}

		// free g
		g.dispose();

		// Zoom to fit if no points found
		if (pointsPainted <= 0 && _checkBounds) {
			zoomToFit();
			_recalculate = true;
			repaint();
		}
		_checkBounds = false;
		// enable / disable transparency slider
		_transparencySlider.setEnabled(showMap);
	}


	/**
	 * Paint the points using the given graphics object
	 * @param inG Graphics object to use for painting
	 * @return number of points painted, if any
	 */
	private int paintPoints(Graphics inG)
	{
		// Set up colours
		final ColourScheme cs = Config.getColourScheme();
		final int[] opacities = {255, 190, 130, 80, 40, 0};
		int opacity = 255;
		if (_transparencySlider.getValue() < 0)
			opacity = opacities[-1 - _transparencySlider.getValue()];
		final Color pointColour  = makeTransparentColour(cs.getColour(ColourScheme.IDX_POINT), opacity);
		final Color rangeColour  = makeTransparentColour(cs.getColour(ColourScheme.IDX_SELECTION), opacity);
		final Color currentColour = makeTransparentColour(cs.getColour(ColourScheme.IDX_PRIMARY), opacity);
		final Color secondColour = makeTransparentColour(cs.getColour(ColourScheme.IDX_SECONDARY), opacity);
		final Color textColour   = makeTransparentColour(cs.getColour(ColourScheme.IDX_TEXT), opacity);

		final int winWidth  = getWidth();
		final int winHeight = getHeight();
		final int halfWinWidth  = winWidth / 2;
		final int halfWinHeight = winHeight / 2;

		final int numPoints = _track.getNumPoints();
		final int[] xPixels = new int[numPoints];
		final int[] yPixels = new int[numPoints];

		// try to set line width for painting
		if (inG instanceof Graphics2D)
		{
			int lineWidth = Config.getConfigInt(Config.KEY_LINE_WIDTH);
			if (lineWidth < 1 || lineWidth > 4) {lineWidth = 2;}
			((Graphics2D) inG).setStroke(new BasicStroke(lineWidth));
		}
		int pointsPainted = 0;
		// draw track points
		inG.setColor(pointColour);
		int prevX = -1, prevY = -1;
		boolean connectPoints = _connectCheckBox.isSelected();
		boolean prevPointVisible = false, currPointVisible = false;
		boolean anyWaypoints = false;
		boolean isWaypoint = false;
		for (int i=0; i<numPoints; i++)
		{
			// Calculate pixel position of point from its x, y coordinates
			int px = halfWinWidth  + _mapPosition.getXFromCentre(_track.getX(i));
			int py = halfWinHeight + _mapPosition.getYFromCentre(_track.getY(i));
			px = wrapLongitudeValue(px, winWidth, _mapPosition.getZoom());
			// Remember these calculated pixel values so they don't have to be recalculated
			xPixels[i] = px; yPixels[i] = py;

			currPointVisible = px >= 0 && px < winWidth && py >= 0 && py < winHeight;
			isWaypoint = _track.getPoint(i).isWaypoint();
			anyWaypoints = anyWaypoints || isWaypoint;
			if (currPointVisible)
			{
				if (!isWaypoint)
				{
					// Draw rectangle for track point
					if (_track.getPoint(i).getDeleteFlag()) {
						inG.setColor(currentColour);
					}
					else {
						inG.setColor(pointColour);
					}
					inG.drawRect(px-2, py-2, 3, 3);
					pointsPainted++;
				}
			}
			if (!isWaypoint)
			{
				// Connect track points if either of them are visible
				if (connectPoints && (currPointVisible || prevPointVisible)
				 && !(prevX == -1 && prevY == -1)
				 && !_track.getPoint(i).getSegmentStart())
				{
					inG.drawLine(prevX, prevY, px, py);
				}
				prevX = px; prevY = py;
			}
			prevPointVisible = currPointVisible;
		}

		// Loop over points, just drawing blobs for waypoints
		inG.setColor(textColour);
		FontMetrics fm = inG.getFontMetrics();
		int nameHeight = fm.getHeight();
		if (anyWaypoints)
		{
			for (int i=0; i<_track.getNumPoints(); i++)
			{
				if (_track.getPoint(i).isWaypoint())
				{
					int px = xPixels[i];
					int py = yPixels[i];
					if (px >= 0 && px < winWidth && py >= 0 && py < winHeight)
					{
						inG.fillRect(px-3, py-3, 6, 6);
						pointsPainted++;
					}
				}
			}
			// Loop over points again, now draw names for waypoints
			for (int i=0; i<_track.getNumPoints(); i++)
			{
				if (_track.getPoint(i).isWaypoint())
				{
					int px = xPixels[i];
					int py = yPixels[i];
					if (px >= 0 && px < winWidth && py >= 0 && py < winHeight)
					{
						// Figure out where to draw waypoint name so it doesn't obscure track
						String waypointName = _track.getPoint(i).getWaypointName();
						int nameWidth = fm.stringWidth(waypointName);
						boolean drawnName = false;
						// Make arrays for coordinates right left up down
						int[] nameXs = {px + 2, px - nameWidth - 2, px - nameWidth/2, px - nameWidth/2};
						int[] nameYs = {py + (nameHeight/2), py + (nameHeight/2), py - 2, py + nameHeight + 2};
						for (int extraSpace = 4; extraSpace < 13 && !drawnName; extraSpace+=2)
						{
							// Shift arrays for coordinates right left up down
							nameXs[0] += 2; nameXs[1] -= 2;
							nameYs[2] -= 2; nameYs[3] += 2;
							// Check each direction in turn right left up down
							for (int a=0; a<4; a++)
							{
								if (nameXs[a] > 0 && (nameXs[a] + nameWidth) < winWidth
									&& nameYs[a] < winHeight && (nameYs[a] - nameHeight) > 0
									&& !overlapsPoints(nameXs[a], nameYs[a], nameWidth, nameHeight, textColour))
								{
									// Found a rectangle to fit - draw name here and quit
									inG.drawString(waypointName, nameXs[a], nameYs[a]);
									drawnName = true;
									break;
								}
							}
						}
					}
				}
			}
		}
		// Loop over points, drawing blobs for photo / audio points
		inG.setColor(secondColour);
		for (int i=0; i<_track.getNumPoints(); i++)
		{
			if (_track.getPoint(i).hasMedia())
			{
				int px = xPixels[i];
				int py = yPixels[i];
				if (px >= 0 && px < winWidth && py >= 0 && py < winHeight)
				{
					inG.drawRect(px-1, py-1, 2, 2);
					inG.drawRect(px-2, py-2, 4, 4);
					pointsPainted++;
				}
			}
		}

		// Draw selected range
		if (_selection.hasRangeSelected())
		{
			inG.setColor(rangeColour);
			for (int i=_selection.getStart(); i<=_selection.getEnd(); i++)
			{
				int px = xPixels[i];
				int py = yPixels[i];
				inG.drawRect(px-1, py-1, 2, 2);
			}
		}

		// Draw crosshairs at selected point
		int selectedPoint = _selection.getCurrentPointIndex();
		if (selectedPoint >= 0)
		{
			int px = xPixels[selectedPoint];
			int py = yPixels[selectedPoint];
			inG.setColor(currentColour);
			// crosshairs
			inG.drawLine(px, 0, px, winHeight);
			inG.drawLine(0, py, winWidth, py);
		}
		// Return the number of points painted
		return pointsPainted;
	}

	/**
	 * Wrap the given pixel value if appropriate and possible
	 * @param inPx Pixel x coordinate
	 * @param inWinWidth window width in pixels
	 * @param inZoom zoom level
	 * @return modified pixel x coordinate
	 */
	private static int wrapLongitudeValue(int inPx, int inWinWidth, int inZoom)
	{
		if (inPx > inWinWidth)
		{
			// Pixel is too far right, could we wrap it back onto the screen?
			int px = inPx;
			while (px > inWinWidth) {
				px -= (256 << inZoom);
			}
			if (px >= 0) {
				return px; // successfully wrapped back onto the screen
			}
		}
		else if (inPx < 0)
		{
			// Pixel is too far left, could we wrap it back onto the screen?
			int px = inPx;
			while (px < 0) {
				px += (256 << inZoom);
			}
			if (px < inWinWidth) {
				return px; // successfully wrapped back onto the screen
			}
		}
		// Either it's already on the screen or couldn't be wrapped
		return inPx;
	}

	/**
	 * Draw the lines while dragging a point
	 * @param inG graphics object
	 * @param inPrevIndex index of point to draw from
	 * @param inNextIndex index of point to draw to
	 */
	private void drawDragLines(Graphics inG, int inPrevIndex, int inNextIndex)
	{
		inG.setColor(Config.getColourScheme().getColour(ColourScheme.IDX_POINT));
		// line from prev point to cursor
		if (inPrevIndex > -1 && !_track.getPoint(inPrevIndex+1).getSegmentStart())
		{
			final int px = getWidth() / 2 + _mapPosition.getXFromCentre(_track.getX(inPrevIndex));
			final int py = getHeight() / 2 + _mapPosition.getYFromCentre(_track.getY(inPrevIndex));
			inG.drawLine(px, py, _dragToX, _dragToY);
		}
		if (inNextIndex < _track.getNumPoints() && !_track.getPoint(inNextIndex).getSegmentStart())
		{
			final int px = getWidth() / 2 + _mapPosition.getXFromCentre(_track.getX(inNextIndex));
			final int py = getHeight() / 2 + _mapPosition.getYFromCentre(_track.getY(inNextIndex));
			inG.drawLine(px, py, _dragToX, _dragToY);
		}
	}

	/**
	 * Tests whether there are any dark pixels within the specified x,y rectangle
	 * @param inX left X coordinate
	 * @param inY bottom Y coordinate
	 * @param inWidth width of rectangle
	 * @param inHeight height of rectangle
	 * @param inTextColour colour of text
	 * @return true if the rectangle overlaps stuff too close to the given colour
	 */
	private boolean overlapsPoints(int inX, int inY, int inWidth, int inHeight, Color inTextColour)
	{
		// each of the colour channels must be further away than this to count as empty
		final int BRIGHTNESS_LIMIT = 80;
		final int textRGB = inTextColour.getRGB();
		final int textLow = textRGB & 255;
		final int textMid = (textRGB >> 8) & 255;
		final int textHigh = (textRGB >> 16) & 255;
		try
		{
			// loop over x coordinate of rectangle
			for (int x=0; x<inWidth; x++)
			{
				// loop over y coordinate of rectangle
				for (int y=0; y<inHeight; y++)
				{
					int pixelColor = _mapImage.getRGB(inX + x, inY - y);
					// split into four components rgba
					int pixLow = pixelColor & 255;
					int pixMid = (pixelColor >> 8) & 255;
					int pixHigh = (pixelColor >> 16) & 255;
					//int fourthBit = (pixelColor >> 24) & 255; // alpha ignored
					// If colours are too close in any channel then it's an overlap
					if (Math.abs(pixLow-textLow) < BRIGHTNESS_LIMIT ||
						Math.abs(pixMid-textMid) < BRIGHTNESS_LIMIT ||
						Math.abs(pixHigh-textHigh) < BRIGHTNESS_LIMIT) {return true;}
				}
			}
		}
		catch (NullPointerException e) {
			// ignore null pointers, just return false
		}
		return false;
	}

	/**
	 * Make a semi-transparent colour for drawing with
	 * @param inColour base colour (fully opaque)
	 * @param inOpacity opacity where 0=invisible and 255=full
	 * @return new colour object
	 */
	private static Color makeTransparentColour(Color inColour, int inOpacity)
	{
		if (inOpacity > 240) return inColour;
		return new Color(inColour.getRed(), inColour.getGreen(), inColour.getBlue(), inOpacity);
	}

	/**
	 * Inform that tiles have been updated and the map can be repainted
	 * @param inIsOk true if data loaded ok, false for error
	 */
	public synchronized void tilesUpdated(boolean inIsOk)
	{
		// Show message if loading failed (but not too many times)
		if (!inIsOk && !_shownOsmErrorAlready && _mapCheckBox.isSelected())
		{
			_shownOsmErrorAlready = true;
			// use separate thread to show message about failing to load osm images
			new Thread(new Runnable() {
				public void run() {
					try {Thread.sleep(500);} catch (InterruptedException ie) {}
					_app.showErrorMessage("error.osmimage.dialogtitle", "error.osmimage.failed");
				}
			}).start();
		}
		_recalculate = true;
		repaint();
	}

	/**
	 * Zoom out, if not already at minimum zoom
	 */
	public void zoomOut()
	{
		_mapPosition.zoomOut();
		_recalculate = true;
		repaint();
	}

	/**
	 * Zoom in, if not already at maximum zoom
	 */
	public void zoomIn()
	{
		_mapPosition.zoomIn();
		_recalculate = true;
		repaint();
	}

	/**
	 * Pan map
	 * @param inDeltaX x shift
	 * @param inDeltaY y shift
	 */
	public void panMap(int inDeltaX, int inDeltaY)
	{
		_mapPosition.pan(inDeltaX, inDeltaY);
		_recalculate = true;
		repaint();
	}

	/**
	 * Create a DataPoint object from the given click coordinates
	 * @param inX x coordinate of click
	 * @param inY y coordinate of click
	 * @return DataPoint with given coordinates and no altitude
	 */
	private DataPoint createPointFromClick(int inX, int inY)
	{
		double lat = MapUtils.getLatitudeFromY(_mapPosition.getYFromPixels(inY, getHeight()));
		double lon = MapUtils.getLongitudeFromX(_mapPosition.getXFromPixels(inX, getWidth()));
		return new DataPoint(new Latitude(lat, Coordinate.FORMAT_NONE),
			new Longitude(lon, Coordinate.FORMAT_NONE), null);
	}

	/**
	 * Move a DataPoint object to the given mouse coordinates
	 * @param startX start x coordinate of mouse
	 * @param startY start y coordinate of mouse
	 * @param endX end x coordinate of mouse
	 * @param endY end y coordinate of mouse
	 */
	private void movePointToMouse(int startX, int startY, int endX, int endY )
	{
		double lat1 = MapUtils.getLatitudeFromY(_mapPosition.getYFromPixels(startY, getHeight()));
		double lon1 = MapUtils.getLongitudeFromX(_mapPosition.getXFromPixels(startX, getWidth()));
		double lat_delta = MapUtils.getLatitudeFromY(_mapPosition.getYFromPixels(endY, getHeight())) - lat1;
		double lon_delta = MapUtils.getLongitudeFromX(_mapPosition.getXFromPixels(endX, getWidth())) - lon1;

		DataPoint point = _trackInfo.getCurrentPoint();
		if (point == null) {
			return;
		}

		// Make lists for edit and undo, and add each changed field in turn
		FieldEditList editList = new FieldEditList();
		FieldEditList undoList = new FieldEditList();

		// Check field list
		FieldList fieldList = _track.getFieldList();
		int numFields = fieldList.getNumFields();
		for (int i=0; i<numFields; i++)
		{
			Field field = fieldList.getField(i);
			if (field == Field.LATITUDE) {
				editList.addEdit(new FieldEdit(field, Double.toString(point.getLatitude().getDouble() + lat_delta)));
				undoList.addEdit(new FieldEdit(field, point.getFieldValue(Field.LATITUDE)));
			}
			else if (field == Field.LONGITUDE) {
				editList.addEdit(new FieldEdit(field, Double.toString(point.getLongitude().getDouble() + lon_delta)));
				undoList.addEdit(new FieldEdit(field, point.getFieldValue(Field.LONGITUDE)));
			}
		}
		_app.completePointEdit(editList, undoList);
	}


	/**
	 * @see javax.swing.JComponent#getMinimumSize()
	 */
	public Dimension getMinimumSize()
	{
		final Dimension minSize = new Dimension(512, 300);
		return minSize;
	}

	/**
	 * @see javax.swing.JComponent#getPreferredSize()
	 */
	public Dimension getPreferredSize()
	{
		return getMinimumSize();
	}


	/**
	 * Respond to mouse click events
	 * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
	 */
	public void mouseClicked(MouseEvent inE)
	{
		if (_track != null && _track.getNumPoints() > 0)
		{
			 // select point if it's a left-click
			if (!inE.isMetaDown())
			{
				if (inE.getClickCount() == 1)
				{
					// single click
					if (_drawMode == MODE_DEFAULT)
					{
						int pointIndex = _clickedPoint;
						if (pointIndex == INDEX_UNKNOWN)
						{
							// index hasn't been calculated yet
							pointIndex = _track.getNearestPointIndex(
							 _mapPosition.getXFromPixels(inE.getX(), getWidth()),
							 _mapPosition.getYFromPixels(inE.getY(), getHeight()),
							 _mapPosition.getBoundsFromPixels(CLICK_SENSITIVITY), false);
						}
						// Extend selection for shift-click
						if (inE.isShiftDown()) {
							_trackInfo.extendSelection(pointIndex);
						}
						else {
							_trackInfo.selectPoint(pointIndex);
						}
					}
					else if (_drawMode == MODE_DRAW_POINTS_START)
					{
						_app.createPoint(createPointFromClick(inE.getX(), inE.getY()));
						_dragToX = inE.getX();
						_dragToY = inE.getY();
						_drawMode = MODE_DRAW_POINTS_CONT;
					}
					else if (_drawMode == MODE_DRAW_POINTS_CONT)
					{
						DataPoint point = createPointFromClick(inE.getX(), inE.getY());
						_app.createPoint(point);
						point.setSegmentStart(false);
					}
				}
				else if (inE.getClickCount() == 2)
				{
					// double click
					if (_drawMode == MODE_DEFAULT) {
						panMap(inE.getX() - getWidth()/2, inE.getY() - getHeight()/2);
						zoomIn();
					}
					else if (_drawMode == MODE_DRAW_POINTS_START || _drawMode == MODE_DRAW_POINTS_CONT) {
						_drawMode = MODE_DEFAULT;
					}
				}
			}
			else
			{
				// show the popup menu for right-clicks
				_popupMenuX = inE.getX();
				_popupMenuY = inE.getY();
				_popup.show(this, _popupMenuX, _popupMenuY);
			}
		}
		// Reset app mode
		_app.setCurrentMode(App.AppMode.NORMAL);
		if (_drawMode == MODE_MARK_RECTANGLE) _drawMode = MODE_DEFAULT;
	}

	/**
	 * Ignore mouse enter events
	 * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
	 */
	public void mouseEntered(MouseEvent inE)
	{
		// ignore
	}

	/**
	 * Ignore mouse exited events
	 * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
	 */
	public void mouseExited(MouseEvent inE)
	{
		// ignore
	}

	/**
	 * React to mouse pressed events to initiate a point drag
	 * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
	 */
	public void mousePressed(MouseEvent inE)
	{
		_clickedPoint = INDEX_UNKNOWN;
		if (_track == null || _track.getNumPoints() <= 0)
			return;
		if (!inE.isMetaDown())
		{
			// Left mouse drag - check if point is near; if so select it for dragging
			if (_drawMode == MODE_DEFAULT)
			{
				/* Drag points if edit mode is enabled OR ALT is pressed */
				if (_editmodeCheckBox.isSelected() || inE.isAltDown() || inE.isAltGraphDown())
				{
					final double clickX = _mapPosition.getXFromPixels(inE.getX(), getWidth());
					final double clickY = _mapPosition.getYFromPixels(inE.getY(), getHeight());
					final double clickSens = _mapPosition.getBoundsFromPixels(CLICK_SENSITIVITY);
					_clickedPoint = _track.getNearestPointIndex(clickX, clickY, clickSens, false);

					if (_clickedPoint >= 0)
					{
						// TODO: maybe use another color of the cross or remove the cross while dragging???

						_trackInfo.selectPoint(_clickedPoint);
						if (_trackInfo.getCurrentPoint() != null)
						{
							_drawMode = MODE_DRAG_POINT;
							_dragFromX = _dragToX = inE.getX();
							_dragFromY = _dragToY = inE.getY();
						}
					}
					else
					{
						// Not a click on a point, so check half-way between two (connected) trackpoints
						int midpointIndex = _midpoints.getNearestPointIndex(clickX, clickY, clickSens);
						if (midpointIndex > 0)
						{
							_drawMode = MODE_CREATE_MIDPOINT;
							_clickedPoint = midpointIndex;
							_dragFromX = _dragToX = inE.getX();
							_dragFromY = _dragToY = inE.getY();
						}
					}
				}
			}
		}
		// else right-press ignored
	}

	/**
	 * Respond to mouse released events
	 * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
	 */
	public void mouseReleased(MouseEvent inE)
	{
		_recalculate = true;

		if (_drawMode == MODE_DRAG_POINT)
		{
			if (Math.abs(_dragToX - _dragFromX) > 2
				|| Math.abs(_dragToY - _dragFromY) > 2)
			{
				movePointToMouse(_dragFromX, _dragFromY, _dragToX, _dragToY );
			}
			_drawMode = MODE_DEFAULT;
		}
		else if (_drawMode == MODE_CREATE_MIDPOINT)
		{
			_drawMode = MODE_DEFAULT;
			_app.createPoint(createPointFromClick(_dragToX, _dragToY), _clickedPoint);
		}
		else if (_drawMode == MODE_ZOOM_RECT)
		{
			if (Math.abs(_dragToX - _dragFromX) > 20
			 && Math.abs(_dragToY - _dragFromY) > 20)
			{
				_mapPosition.zoomToPixels(_dragFromX, _dragToX, _dragFromY, _dragToY, getWidth(), getHeight());
			}
			_drawMode = MODE_DEFAULT;
		}
		else if (_drawMode == MODE_MARK_RECTANGLE)
		{
			// Reset app mode
			_app.setCurrentMode(App.AppMode.NORMAL);
			_drawMode = MODE_DEFAULT;
			// Call a function to mark the points
			MarkPointsInRectangleFunction marker = new MarkPointsInRectangleFunction(_app);
			double lon1 = MapUtils.getLongitudeFromX(_mapPosition.getXFromPixels(_dragFromX, getWidth()));
			double lat1 = MapUtils.getLatitudeFromY(_mapPosition.getYFromPixels(_dragFromY, getHeight()));
			double lon2 = MapUtils.getLongitudeFromX(_mapPosition.getXFromPixels(_dragToX, getWidth()));
			double lat2 = MapUtils.getLatitudeFromY(_mapPosition.getYFromPixels(_dragToY, getHeight()));
			// Invalidate rectangle if pixel coords are (-1,-1)
			if (_dragFromX < 0 || _dragFromY < 0) {
				lon1 = lon2;
				lat1 = lat2;
			}
			marker.setRectCoords(lon1, lat1, lon2, lat2);
			marker.begin();
		}
		_dragFromX = _dragFromY = -1;
		repaint();
	}

	/**
	 * Respond to mouse drag events
	 * @see java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent)
	 */
	public void mouseDragged(MouseEvent inE)
	{
		if (!inE.isMetaDown())
		{
			// Left mouse drag - either drag the point or pan the map
			if (_drawMode == MODE_DRAG_POINT || _drawMode == MODE_CREATE_MIDPOINT)
			{
				// move point
				_dragToX = inE.getX();
				_dragToY = inE.getY();
				_recalculate = true;
				repaint();
			}
			else if (_drawMode == MODE_MARK_RECTANGLE)
			{
				// draw a rectangle for marking points
				if (_dragFromX == -1) {
					_dragFromX = inE.getX();
					_dragFromY = inE.getY();
				}
				_dragToX = inE.getX();
				_dragToY = inE.getY();
				repaint();
			}
			else
			{
				// regular left-drag pans map by appropriate amount
				if (_dragFromX != -1)
				{
					panMap(_dragFromX - inE.getX(), _dragFromY - inE.getY());
				}
				_dragFromX = _dragToX = inE.getX();
				_dragFromY = _dragToY = inE.getY();
			}
		}
		else
		{
			// Right-click and drag - update rectangle
			_drawMode = MODE_ZOOM_RECT;
			if (_dragFromX == -1) {
				_dragFromX = inE.getX();
				_dragFromY = inE.getY();
			}
			_dragToX = inE.getX();
			_dragToY = inE.getY();
			repaint();
		}
	}

	/**
	 * Respond to mouse move events without button pressed
	 * @param inEvent ignored
	 */
	public void mouseMoved(MouseEvent inEvent)
	{
		boolean useCrosshairs = false;
		boolean useResize     = false;
		// Ignore unless we're drawing points
		if (_drawMode == MODE_DRAW_POINTS_CONT)
		{
			_dragToX = inEvent.getX();
			_dragToY = inEvent.getY();
			repaint();
		}
		else if (_drawMode == MODE_MARK_RECTANGLE) {
			useResize = true;
		}
		else if (_editmodeCheckBox.isSelected() || inEvent.isAltDown() || inEvent.isAltGraphDown())
		{
			// Try to find a point or a midpoint at this location, and if there is one
			// then change the cursor to crosshairs
			final double clickX = _mapPosition.getXFromPixels(inEvent.getX(), getWidth());
			final double clickY = _mapPosition.getYFromPixels(inEvent.getY(), getHeight());
			final double clickSens = _mapPosition.getBoundsFromPixels(CLICK_SENSITIVITY);
			useCrosshairs = (_track.getNearestPointIndex(clickX, clickY, clickSens, false) >= 0
				|| _midpoints.getNearestPointIndex(clickX, clickY, clickSens) >= 0
			);
		}
		if (useCrosshairs && !isCursorSet()) {
			setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
		}
		else if (useResize && !isCursorSet()) {
			setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
		}
		else if (!useCrosshairs && !useResize && isCursorSet()) {
			setCursor(null);
		}
	}

	/**
	 * Respond to status bar message from broker
	 * @param inMessage message, ignored
	 */
	public void actionCompleted(String inMessage)
	{
		// ignore
	}

	/**
	 * Respond to data updated message from broker
	 * @param inUpdateType type of update
	 */
	public void dataUpdated(byte inUpdateType)
	{
		_recalculate = true;
		if ((inUpdateType & DataSubscriber.DATA_ADDED_OR_REMOVED) > 0) {
			_checkBounds = true;
		}
		if ((inUpdateType & DataSubscriber.MAPSERVER_CHANGED) > 0) {
			_tileManager.resetConfig();
		}
		if ((inUpdateType & (DataSubscriber.DATA_ADDED_OR_REMOVED + DataSubscriber.DATA_EDITED)) > 0) {
			_midpoints.updateData(_track);
		}
		// See if rect mode has been activated
		if (_app.getCurrentMode() == App.AppMode.DRAWRECT)
		{
			_drawMode = MODE_MARK_RECTANGLE;
			if (!isCursorSet()) {
				setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
			}
		}
		repaint();
		// enable or disable components
		boolean hasData = _track.getNumPoints() > 0;
		_topPanel.setVisible(hasData);
		_sidePanel.setVisible(hasData);
		// grab focus for the key presses
		this.requestFocus();
	}

	/**
	 * Respond to key presses on the map canvas
	 * @param inE key event
	 */
	public void keyPressed(KeyEvent inE)
	{
		int code = inE.getKeyCode();
		int currPointIndex = _selection.getCurrentPointIndex();
		// Check for Ctrl key (for Linux/Win) or meta key (Clover key for Mac)
		if (inE.isControlDown() || inE.isMetaDown())
		{
			// Shift as well makes things faster
			final int pointIncrement = inE.isShiftDown()?3:1;
			// Check for arrow keys to zoom in and out
			if (code == KeyEvent.VK_UP)
				zoomIn();
			else if (code == KeyEvent.VK_DOWN)
				zoomOut();
			// Key nav for next/prev point
			else if (code == KeyEvent.VK_LEFT && currPointIndex > 0)
				_trackInfo.incrementPointIndex(-pointIncrement);
			else if (code == KeyEvent.VK_RIGHT)
				_trackInfo.incrementPointIndex(pointIncrement);
			else if (code == KeyEvent.VK_PAGE_UP)
				_trackInfo.selectPoint(Checker.getPreviousSegmentStart(
					_trackInfo.getTrack(), _trackInfo.getSelection().getCurrentPointIndex()));
			else if (code == KeyEvent.VK_PAGE_DOWN)
				_trackInfo.selectPoint(Checker.getNextSegmentStart(
					_trackInfo.getTrack(), _trackInfo.getSelection().getCurrentPointIndex()));
			// Check for home and end
			else if (code == KeyEvent.VK_HOME)
				_trackInfo.selectPoint(0);
			else if (code == KeyEvent.VK_END)
				_trackInfo.selectPoint(_trackInfo.getTrack().getNumPoints()-1);
		}
		else
		{
			// Check for arrow keys to pan
			int upwardsPan = 0;
			if (code == KeyEvent.VK_UP)
				upwardsPan = -PAN_DISTANCE;
			else if (code == KeyEvent.VK_DOWN)
				upwardsPan = PAN_DISTANCE;
			int rightwardsPan = 0;
			if (code == KeyEvent.VK_RIGHT)
				rightwardsPan = PAN_DISTANCE;
			else if (code == KeyEvent.VK_LEFT)
				rightwardsPan = -PAN_DISTANCE;
			panMap(rightwardsPan, upwardsPan);
			// Check for escape
			if (code == KeyEvent.VK_ESCAPE)
				_drawMode = MODE_DEFAULT;
			// Check for backspace key to delete current point (delete key already handled by menu)
			else if (code == KeyEvent.VK_BACK_SPACE && currPointIndex >= 0) {
				_app.deleteCurrentPoint();
			}
		}
	}

	/**
	 * @param inE key released event, ignored
	 */
	public void keyReleased(KeyEvent e)
	{
		// ignore
	}

	/**
	 * @param inE key typed event, ignored
	 */
	public void keyTyped(KeyEvent inE)
	{
		// ignore
	}

	/**
	 * @param inE mouse wheel event indicating scroll direction
	 */
	public void mouseWheelMoved(MouseWheelEvent inE)
	{
		int clicks = inE.getWheelRotation();
		if (clicks < 0) {
			panMap((inE.getX() - getWidth()/2)/2, (inE.getY() - getHeight()/2)/2);
			zoomIn();
		}
		else if (clicks > 0) {
			panMap(-(inE.getX() - getWidth()/2), -(inE.getY() - getHeight()/2));
			zoomOut();
		}
	}

	/**
	 * @return current map position
	 */
	public MapPosition getMapPosition()
	{
		return _mapPosition;
	}
}
