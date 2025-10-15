// third-party
import { Theme, SxProps } from '@mui/material/styles';
import { PopupProps } from 'react-map-gl';

// material-ui

// project-import
import PopupStyled from './PopupStyled';

interface Props extends PopupProps {
  sx?: SxProps<Theme>;
}

// ==============================|| MAP BOX - MODAL ||============================== //

const MapPopup = ({ sx, children, ...other }: Props) => {
  return (
    <PopupStyled anchor="bottom" sx={sx || {}} {...other}>
      {children}
    </PopupStyled>
  );
};

export default MapPopup;
