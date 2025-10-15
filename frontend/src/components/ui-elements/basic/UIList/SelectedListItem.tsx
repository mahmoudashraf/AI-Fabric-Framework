'use client';
import DescriptionTwoToneIcon from '@mui/icons-material/DescriptionTwoTone';
import HomeTwoToneIcon from '@mui/icons-material/HomeTwoTone';
import ListAltTwoToneIcon from '@mui/icons-material/ListAltTwoTone';
import { List, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import React from 'react';

// project improts
import useConfig from 'hooks/useConfig';

// material-ui

// assets

// ================================|| UI LIST - SELECTED LIST ||================================ //

export default function SelectedListItem() {
  const { borderRadius } = useConfig();

  const [selectedIndex, setSelectedIndex] = React.useState(0);
  const handleListItemClick = (_event: React.SyntheticEvent, index: number) => {
    setSelectedIndex(index);
  };

  return (
    <div>
      <List component="nav" aria-label="main mailbox folders">
        <ListItemButton
          selected={selectedIndex === 0}
          onClick={event => handleListItemClick(event, 0)}
          sx={{ borderRadius: `${borderRadius}px` }}
        >
          <ListItemIcon>
            <HomeTwoToneIcon sx={{ fontSize: '1.3rem' }} />
          </ListItemIcon>
          <ListItemText primary="Sample Page" />
        </ListItemButton>
        <ListItemButton
          selected={selectedIndex === 1}
          onClick={event => handleListItemClick(event, 1)}
          sx={{ borderRadius: `${borderRadius}px`, mt: 0.625 }}
        >
          <ListItemIcon>
            <DescriptionTwoToneIcon sx={{ fontSize: '1.3rem' }} />
          </ListItemIcon>
          <ListItemText primary="Elements" />
        </ListItemButton>
        <ListItemButton
          selected={selectedIndex === 2}
          onClick={event => handleListItemClick(event, 2)}
          sx={{ borderRadius: `${borderRadius}px`, mt: 0.625 }}
        >
          <ListItemIcon>
            <ListAltTwoToneIcon sx={{ fontSize: '1.3rem' }} />
          </ListItemIcon>
          <ListItemText primary="Page Layouts" />
        </ListItemButton>
      </List>
    </div>
  );
}
