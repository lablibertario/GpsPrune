package tim.prune.undo;

import tim.prune.I18nManager;
import tim.prune.data.DataPoint;
import tim.prune.data.TrackInfo;

/**
 * Operation to undo a delete of a single point
 */
public class UndoDeletePoint implements UndoOperation
{
	private int _pointIndex = -1;
	private DataPoint _point = null;
	private int _photoIndex = -1;


	/**
	 * Constructor
	 * @param inPointIndex index number of point within track
	 * @param inPoint data point
	 * @param inPhotoIndex index number of photo within photo list
	 */
	public UndoDeletePoint(int inPointIndex, DataPoint inPoint, int inPhotoIndex)
	{
		_pointIndex = inPointIndex;
		_point = inPoint;
		_photoIndex = inPhotoIndex;
	}


	/**
	 * @return description of operation including point name if any
	 */
	public String getDescription()
	{
		String desc = I18nManager.getText("undo.deletepoint");
		String pointName = _point.getWaypointName();
		if (pointName != null && !pointName.equals(""))
			desc = desc + " " + pointName;
		return desc;
	}


	/**
	 * Perform the undo operation on the given Track
	 * @param inTrackInfo TrackInfo object on which to perform the operation
	 */
	public void performUndo(TrackInfo inTrackInfo) throws UndoException
	{
		// restore point into track
		if (!inTrackInfo.getTrack().insertPoint(_point, _pointIndex))
		{
			throw new UndoException(getDescription());
		}
		// Re-attach / Re-insert photo into list if necessary
		if (_point.getPhoto() != null && _photoIndex > -1)
		{
			// Check if photo is still in list
			if (!inTrackInfo.getPhotoList().contains(_point.getPhoto()))
			{
				// photo has been removed - need to reinsert
				inTrackInfo.getPhotoList().addPhoto(_point.getPhoto(), _photoIndex);
			}
			// Ensure that photo is associated with point
			_point.getPhoto().setDataPoint(_point);
		}
	}
}