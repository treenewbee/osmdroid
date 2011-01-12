package org.osmdroid.views.overlay;

import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.util.BoundingBoxE6;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.util.constants.MapViewConstants;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.MotionEvent;

/**
 * Draws a mini-map as an overlay layer. It currently uses its own MapTileProviderBasic or a tile
 * provider supplied to it. Do NOT share a tile provider amongst multiple tile drawing overlays - it
 * will create an under-sized cache.
 * 
 * @author Marc Kurtz
 * 
 */
public class MinimapOverlay extends TilesOverlay implements MapViewConstants {

	// TODO: Make these constants adjustable
	private static final int MAP_WIDTH = 100;
	private static final int MAP_HEIGHT = 100;
	private static final int MAP_PADDING = 10;
	private final Paint mPaint;
	private int mWorldSize_2;

	// The Mercator coordinates of what is on the screen
	final private Rect mViewportRect = new Rect();

	// The Mercator coordinates of the map tile area we are interested in the target zoom level
	final private Rect mTileArea = new Rect();

	// The Canvas coordinates where the minimap should be drawn
	final private Rect mMiniMapCanvasRect = new Rect();

	// Stores the intersection of the minimap and the Canvas clipping area
	final private Rect mIntersectionRect = new Rect();
	private int mZoomDifference;

	/**
	 * Creates a {@link MinimapOverlay} with the supplied tile provider. The {@link Handler} passed
	 * in is typically the same handler being used by the main map.
	 * 
	 * @param pContext
	 *            a context
	 * @param tileRequestCompleteHandler
	 *            a handler for the tile request complete notifications
	 * @param pTileProvider
	 *            a tile provider
	 */
	public MinimapOverlay(final Context pContext, final Handler pTileRequestCompleteHandler,
			final MapTileProviderBase pTileProvider, final int pZoomDifference) {
		super(pTileProvider, pContext);
		setZoomDifference(pZoomDifference);

		mTileProvider.setTileRequestCompleteHandler(pTileRequestCompleteHandler);

		mPaint = new Paint();
		mPaint.setColor(Color.GRAY);
		mPaint.setStyle(Style.FILL);
		mPaint.setStrokeWidth(2);
	}

	/**
	 * Creates a {@link MinimapOverlay} with the supplied tile provider. The {@link Handler} passed
	 * in is typically the same handler being used by the main map.
	 * 
	 * @param pContext
	 *            a context
	 * @param tileRequestCompleteHandler
	 *            a handler for the tile request complete notifications
	 * @param pTileProvider
	 *            a tile provider
	 */
	public MinimapOverlay(final Context pContext, final Handler pTileRequestCompleteHandler,
			final MapTileProviderBase pTileProvider) {
		this(pContext, pTileRequestCompleteHandler, pTileProvider,
				DEFAULT_ZOOMLEVEL_MINIMAP_DIFFERENCE);
	}

	/**
	 * Creates a {@link MinimapOverlay} that uses its own {@link MapTileProviderBasic}. The
	 * {@link Handler} passed in is typically the same handler being used by the main map.
	 * 
	 * @param pContext
	 *            a context
	 * @param tileRequestCompleteHandler
	 *            a handler for tile request complete notifications
	 */
	public MinimapOverlay(final Context pContext, final Handler pTileRequestCompleteHandler) {
		this(pContext, pTileRequestCompleteHandler, new MapTileProviderBasic(pContext));
	}

	public void setTileSource(final ITileSource pTileSource) {
		mTileProvider.setTileSource(pTileSource);
	}

	public int getZoomDifference() {
		return mZoomDifference;
	}

	public void setZoomDifference(final int zoomDifference) {
		mZoomDifference = zoomDifference;
	}

	@Override
	protected void onDraw(final Canvas pC, final MapView pOsmv) {

		// Don't draw if we are animating
		if (pOsmv.isAnimating())
			return;

		// Calculate the half-world size
		final Projection projection = pOsmv.getProjection();
		final int zoomLevel = projection.getZoomLevel();
		final int tileZoom = projection.getTileMapZoom();
		mWorldSize_2 = 1 << (zoomLevel + tileZoom - 1);

		// Find what's on the screen
		final BoundingBoxE6 boundingBox = projection.getBoundingBox();
		final Point upperLeft = org.osmdroid.views.util.Mercator
				.projectGeoPoint(boundingBox.getLatNorthE6(), boundingBox.getLonWestE6(), zoomLevel
						+ tileZoom, null);
		final Point lowerRight = org.osmdroid.views.util.Mercator
				.projectGeoPoint(boundingBox.getLatSouthE6(), boundingBox.getLonEastE6(), zoomLevel
						+ tileZoom, null);

		// Save the Mercator coordinates of what is on the screen
		mViewportRect.set(upperLeft.x, upperLeft.y, lowerRight.x, lowerRight.y);

		// Start calculating the tile area with the current viewport
		mTileArea.set(mViewportRect);

		// Get the target zoom level difference.
		int miniMapZoomLevelDifference = getZoomDifference();

		// Make sure the zoom level difference isn't below the minimum zoom level
		if (zoomLevel - getZoomDifference() < mTileProvider.getMinimumZoomLevel())
			miniMapZoomLevelDifference += zoomLevel - getZoomDifference()
					- mTileProvider.getMinimumZoomLevel();

		// Shift the screen coordinates into the target zoom level
		mTileArea.set(mTileArea.left >> miniMapZoomLevelDifference,
				mTileArea.top >> miniMapZoomLevelDifference,
				mTileArea.right >> miniMapZoomLevelDifference,
				mTileArea.bottom >> miniMapZoomLevelDifference);

		// Limit the area we are interested in for tiles to be the MAP_WIDTH by MAP_HEIGHT and
		// centered on the center of the screen
		mTileArea.set(mTileArea.centerX() - (MAP_WIDTH / 2),
				mTileArea.centerY() - (MAP_HEIGHT / 2), mTileArea.centerX() + (MAP_WIDTH / 2),
				mTileArea.centerY() + (MAP_HEIGHT / 2));

		// Get the area where we will draw the minimap in screen coordinates
		mMiniMapCanvasRect.set(mViewportRect.right - MAP_PADDING - MAP_WIDTH, mViewportRect.bottom
				- MAP_PADDING - MAP_HEIGHT, mViewportRect.right - MAP_PADDING, mViewportRect.bottom
				- MAP_PADDING);
		mMiniMapCanvasRect.offset(-mWorldSize_2, -mWorldSize_2);

		// Draw a solid background where the minimap will be drawn with a 2 pixel inset
		pC.drawRect(mMiniMapCanvasRect.left - 2, mMiniMapCanvasRect.top - 2,
				mMiniMapCanvasRect.right + 2, mMiniMapCanvasRect.bottom + 2, mPaint);

		super.drawTiles(pC, projection.getZoomLevel() - miniMapZoomLevelDifference,
				projection.getTileSizePixels(), mTileArea);
	}

	@Override
	protected void onTileReadyToDraw(final Canvas c, final Drawable currentMapTile,
			final Rect tileRect) {

		// Get the offsets for where to draw the tiles relative to where the minimap is located
		final int xOffset = (tileRect.left - mTileArea.left) + (mMiniMapCanvasRect.left);
		final int yOffset = (tileRect.top - mTileArea.top) + (mMiniMapCanvasRect.top);

		// Set the drawable's location
		currentMapTile.setBounds(xOffset, yOffset, xOffset + tileRect.width(),
				yOffset + tileRect.height());

		// Save the current clipping bounds
		final Rect oldClip = c.getClipBounds();

		// Check to see if the drawing area intersects with the minimap area
		if (mIntersectionRect.setIntersect(oldClip, mMiniMapCanvasRect)) {
			// If so, then clip that area
			c.clipRect(mIntersectionRect);

			// Draw the tile, which will be appropriately clipped
			currentMapTile.draw(c);

			// Restore the original clipping bounds
			c.clipRect(oldClip);
		}
	}

	@Override
	protected void onDrawFinished(final Canvas pC, final MapView pOsmv) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean onSingleTapUp(final MotionEvent pEvent, final MapView pMapView) {
		// Consume event so layers underneath don't receive
		if (mMiniMapCanvasRect.contains((int) pEvent.getX() + mViewportRect.left - mWorldSize_2,
				(int) pEvent.getY() + mViewportRect.top - mWorldSize_2))
			return true;

		return false;
	}

	@Override
	public boolean onLongPress(final MotionEvent pEvent, final MapView pMapView) {
		// Consume event so layers underneath don't receive
		if (mMiniMapCanvasRect.contains((int) pEvent.getX() + mViewportRect.left - mWorldSize_2,
				(int) pEvent.getY() + mViewportRect.top - mWorldSize_2))
			return true;

		return false;
	}

	// TODO: This is too "sensitive". Drags will be cancelled across the mini-map even if they are
	// started outside the mini-map. We need to implement a double-click handler for the overlays.

	// @Override
	// public boolean onTouchEvent(final MotionEvent pEvent, final MapView pMapView) {
	// // Consume event so layers underneath don't receive
	// if (mMiniMapCanvasRect.contains((int) pEvent.getX() + mViewportRect.left - mWorldSize_2,
	// (int) pEvent.getY() + mViewportRect.top - mWorldSize_2))
	// return true;
	//
	// return false;
	// }

}