'use client';

import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import MuiAccordion from '@mui/material/Accordion';
import MuiAccordionDetails from '@mui/material/AccordionDetails';
import MuiAccordionSummary from '@mui/material/AccordionSummary';
import Box from '@mui/material/Box';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState, ReactElement } from 'react';

// material-ui

// assets

type AccordionItem = {
  id: string;
  title: ReactElement | string;
  content: ReactElement | string;
  disabled?: boolean;
  expanded?: boolean;
  defaultExpand?: boolean | undefined;
};

interface AccordionProps {
  data: AccordionItem[];
  defaultExpandedId?: string | boolean | null;
  expandIcon?: ReactElement;
  square?: boolean;
  toggle?: boolean;
}

// ==============================|| ACCORDION ||============================== //

const Accordion = ({
  data,
  defaultExpandedId = null,
  expandIcon,
  square,
  toggle,
}: AccordionProps) => {
  const theme = useTheme();

  const [expanded, setExpanded] = useState<string | boolean | null>(null);
  const handleChange =
    (panel: string) => (_event: React.SyntheticEvent<Element, Event>, newExpanded: boolean) => {
      if (toggle) setExpanded(newExpanded ? panel : false);
    };

  useEffect(() => {
    setExpanded(defaultExpandedId);
  }, [defaultExpandedId]);

  return (
    <Box sx={{ width: '100%' }}>
      {data &&
        data.map((item: AccordionItem) => (
          <MuiAccordion
            key={item.id}
            defaultExpanded={!item.disabled && item.defaultExpand || false}
            expanded={
              Boolean((!toggle && !item.disabled && item.expanded) || (toggle && expanded === item.id))
            }
            disabled={item.disabled || false}
            square={square || false}
            onChange={handleChange(item.id)}
          >
            <MuiAccordionSummary
              expandIcon={expandIcon || expandIcon === false ? expandIcon : <ExpandMoreIcon />}
              sx={{
                color: theme.palette.mode === 'dark' ? 'grey.600' : 'grey.900',
                fontWeight: 500,
              }}
            >
              {item.title}
            </MuiAccordionSummary>
            <MuiAccordionDetails>{item.content}</MuiAccordionDetails>
          </MuiAccordion>
        ))}
    </Box>
  );
};

export default Accordion;
