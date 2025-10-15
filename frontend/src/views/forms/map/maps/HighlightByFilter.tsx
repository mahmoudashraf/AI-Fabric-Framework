'use client';

import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState, useCallback, useMemo, memo } from 'react';

// material-ui

// third-party
import { Map, Layer, Source, FillLayer, MapLayerMouseEvent } from 'react-map-gl';

// project-import
import { MapBoxProps } from 'types/map';
import MapControl from 'ui-component/third-party/map/MapControl';
import MapPopup from 'ui-component/third-party/map/MapPopup';

// types

// ==============================|| HIGHLIGHT BY FILTER ||============================== //

const HighlightByFilter = ({ ...other }: MapBoxProps) => {
  const theme = useTheme();

  // @ts-ignore
  const countiesLayer: FillLayer = {
    id: 'counties',
    type: 'fill',
    'source-layer': 'original',
    paint: {
      'fill-outline-color': theme.palette.grey[900],
      'fill-color': theme.palette.grey[900],
      'fill-opacity': 0.12,
    },
  };

  const highlightLayer: FillLayer = {
    id: 'counties-highlighted',
    type: 'fill',
    source: 'counties',
    'source-layer': 'original',
    paint: {
      'fill-outline-color': theme.palette.error.main,
      'fill-color': theme.palette.error.main,
      'fill-opacity': 0.48,
    },
  };

  const [hoverInfo, setHoverInfo] = useState<{
    countyName: string;
    longitude: number;
    latitude: number;
  } | null>(null);

  const onHover = useCallback((event: MapLayerMouseEvent) => {
    const county = event.features && event.features[0];

    setHoverInfo({
      longitude: event.lngLat.lng,
      latitude: event.lngLat.lat,
      countyName: county && county.properties?.COUNTY,
    });
  }, []);

  const selectedCounty = (hoverInfo && hoverInfo.countyName) || '';

  const filter = useMemo(() => ['in', 'COUNTY', selectedCounty], [selectedCounty]);

  return (
    <Map
      {...(other as any)}
      initialViewState={{
        latitude: 38.88,
        longitude: -98,
        zoom: 3,
      }}
      minZoom={2}
      maxZoom={22}
      minPitch={0}
      maxPitch={85}
      interactive={true}
      onMouseMove={onHover}
      interactiveLayerIds={['counties']}
    >
      <MapControl />
      <Source type="vector" url="mapbox://mapbox.82pkq93d">
        <Layer beforeId="waterway-label" {...countiesLayer} />
        {/* @ts-ignore */}
        <Layer beforeId="waterway-label" {...highlightLayer} filter={filter} />
      </Source>

      {selectedCounty && hoverInfo && (
        <MapPopup longitude={hoverInfo.longitude} latitude={hoverInfo.latitude} closeButton={false}>
          <Typography variant="body2" color="secondary.lighter">
            {selectedCounty}
          </Typography>
        </MapPopup>
      )}
    </Map>
  );
};

export default memo(HighlightByFilter);
