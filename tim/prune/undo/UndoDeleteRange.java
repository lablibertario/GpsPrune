package tim.prune.undo;

import tim.prune.I18nManager;
import tim.prune.data.DataPoint;
import tim.prune.data.PhotoList;
import tim.prune.data.TrackInfo;

/**
 * Operation to undo a delete of a range of points
 */
public class UndoDeleteRange implements UndoOperation
{
	private int _startIndex = -1;
	private DataPoint[] _points = null;
	private PhotoList _photoList = null;


	/**
	 * Constructor
	 * @param inIndex index number of point within track
	 * @param inPoint data point
	 */
	public UndoDeleteRange(TrackInfo inTrackInfo)
	{
		_startIndex = inTrackInfo.getSelection().getStart();
		_points = inTrackInfo.cloneSelectedRange();
		_photoList = inTrackInfo.getPhotoList().cloneList();
	}


	/**
	 * @return description of operation including range length
	 */
	public String getDescription()
	{
		return I18nManager.getText("undo.deleterange")
			+ " (" + _points.length + ")";
	}


	/**
	 * Perform the undo operation on the given Track
	 * @param inTrackInfo TrackInfo object on which to perform the operation
	 */
	public void performUndo(TrackInfo inTrackInfo)
	{
		// restore photos to how they were before
		inTrackInfo.getPhotoList().restore(_photoList);
		// reconnect photos to points
		for (int i=0; i<_points.length; i++)
		{
			DataPoint point = _points[i];
			if (point != null && point.getPhoto() != null)
			{
				point.getPhoto().setDataPoint(point);
			}
		}
		// restore point array into track
		inTrackInfo.getTrack().insertRange(_points, _startIndex);
	}
}