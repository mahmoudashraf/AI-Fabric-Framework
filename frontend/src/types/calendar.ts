// third party
import { FormikValues } from 'formik';

// ==============================|| TYPES - CALENDAR ||============================== //

export type Event = {
  id: string;
  allDay: boolean;
  color: string;
  textColor?: string;
  description: string;
  start: Date;
  end: Date;
  title: string;
};

export interface CalendarStateProps {
  events: FormikValues[];
  error: object | string | null;
}
