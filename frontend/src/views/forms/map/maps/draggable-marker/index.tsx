'use client';

import { useState, useCallback, memo } from 'react';

// third-party
import Map, { MarkerDragEvent, LngLat } from 'react-map-gl';

// project-import
import { MapBoxProps } from 'types/map';
import MapControl from 'ui-component/third-party/map/MapControl';
import MapMarker from 'ui-component/third-party/map/MapMarker';

import ControlPanel from './control-panel';

// types

// ==============================|| MAP - DRAGGABLE MARKER ||============================== //

const DraggableMarkers = ({ ...other }: MapBoxProps) => {
  const [marker, setMarker] = useState({
    latitude: 21.2335611,
    longitude: 72.8636084,
  });

  const [events, logEvents] = useState<Record<string, LngLat>>({});

  const onMarkerDragStart = useCallback((event: MarkerDragEvent) => {
    logEvents(_events => ({ ..._events, onDragStart: event.lngLat }) as any);
  }, []);

  const onMarkerDrag = useCallback((event: MarkerDragEvent) => {
    logEvents(_events => ({ ..._events, onDrag: event.lngLat }) as any);

    setMarker({
      longitude: event.lngLat.lng,
      latitude: event.lngLat.lat,
    });
  }, []);

  const onMarkerDragEnd = useCallback((event: MarkerDragEvent) => {
    logEvents(_events => ({ ..._events, onDragEnd: event.lngLat }) as any);
  }, []);

  return (
    <>
      <Map
        {...(other as any)}
        initialViewState={{ latitude: 21.2335611, longitude: 72.8636084, zoom: 6 }}
        minZoom={0}
        maxZoom={22}
        minPitch={0}
        maxPitch={85}
        interactive={true}
      >
        <MapControl />
        <MapMarker
          longitude={marker.longitude}
          latitude={marker.latitude}
          anchor="bottom"
          draggable
          onDragStart={onMarkerDragStart}
          onDrag={onMarkerDrag}
          onDragEnd={onMarkerDragEnd}
        />
      </Map>

      <ControlPanel events={events} />
    </>
  );
};

export default memo(DraggableMarkers);
