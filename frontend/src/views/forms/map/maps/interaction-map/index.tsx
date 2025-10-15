'use client';

import { useState, useCallback, memo } from 'react';

// third-party
import Map from 'react-map-gl';

// project-import
import { MapBoxProps } from 'types/map';
import MapControl from 'ui-component/third-party/map/MapControl';

import ControlPanel from './control-panel';

// types

// ==============================|| MAP BOX - INTERATION MAP ||============================== //

function InteractionMap({ ...other }: MapBoxProps) {
  const [settings, setSettings] = useState({
    minZoom: 0,
    maxZoom: 20,
    minPitch: 0,
    maxPitch: 85,
    dragPan: true,
    boxZoom: true,
    keyboard: true,
    touchZoom: true,
    dragRotate: true,
    scrollZoom: true,
    touchPitch: true,
    touchRotate: true,
    doubleClickZoom: true,
    touchZoomRotate: true,
  });

  const updateSettings = useCallback(
    (name: string, value: boolean | number) =>
      setSettings(prevSettings => ({
        ...prevSettings,
        [name]: value,
      })),
    []
  );

  return (
    <Map
      {...(other as any)}
      {...settings}
      initialViewState={{
        latitude: 37.729,
        longitude: -122.36,
        zoom: 11,
        bearing: 0,
        pitch: 50,
      }}
      minZoom={settings.minZoom ?? 0}
      maxZoom={settings.maxZoom ?? 22}
      minPitch={settings.minPitch ?? 0}
      maxPitch={settings.maxPitch ?? 85}
      interactive={true}
    >
      <MapControl />
      <ControlPanel settings={settings} onChange={updateSettings} />
    </Map>
  );
}

export default memo(InteractionMap);
