'use client';

// material-ui

// third-party
import { DateSelectArg, EventClickArg, EventDropArg } from '@fullcalendar/core';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin, { EventResizeDoneArg } from '@fullcalendar/interaction';
import listPlugin from '@fullcalendar/list';
import FullCalendar from '@fullcalendar/react';
import timeGridPlugin from '@fullcalendar/timegrid';
import timelinePlugin from '@fullcalendar/timeline';
import AddAlarmTwoToneIcon from '@mui/icons-material/AddAlarmTwoTone';
import { Button, Dialog, Theme, useMediaQuery } from '@mui/material';
import { FormikValues } from 'formik';

// project imports
import { useEffect, useRef, useState } from 'react';

import AddEventForm from 'components/application/calendar/AddEventForm';
import CalendarStyled from 'components/application/calendar/CalendarStyled';
import Toolbar from 'components/application/calendar/Toolbar';
import { useCalendar } from 'contexts/CalendarContext';
import { useNotifications } from 'contexts/NotificationContext';
import { useAsyncOperation } from '@/hooks/enterprise';
// import { useCalendarQuery } from 'hooks/useCalendarQuery'; // TODO: Implement this hook
// Using Context API

// assets

// types
import { DateRange } from 'types';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';
import Loader from 'ui-component/Loader';

// ==============================|| APPLICATION CALENDAR ||============================== //

const Calendar = () => {
  const calendarRef = useRef<FullCalendar>(null);
  const matchSm = useMediaQuery((theme: Theme) => theme.breakpoints.down('md'));

  const [loading, setLoading] = useState<boolean>(true);

  // Use Context API for calendar and notifications
  const calendarContext = useCalendar();
  const notificationContext = useNotifications();
  // Calendar data from context
  const events = calendarContext.state.events;

  useEffect(() => {
    // Use Context API for calendar data
    setLoading(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    // Get events data from context
    // Removed local setEvents; FullCalendar reads directly from events
  }, [events]);

  const [date, setDate] = useState(new Date());
  const [view, setView] = useState(matchSm ? 'listWeek' : 'dayGridMonth');

  // calendar toolbar events
  const handleDateToday = () => {
    const calendarEl = calendarRef.current;

    if (calendarEl) {
      const calendarApi = calendarEl.getApi();

      calendarApi.today();
      setDate(calendarApi.getDate());
    }
  };

  const handleViewChange = (newView: string) => {
    const calendarEl = calendarRef.current;

    if (calendarEl) {
      const calendarApi = calendarEl.getApi();

      calendarApi.changeView(newView);
      setView(newView);
    }
  };

  // set calendar view
  useEffect(() => {
    handleViewChange(matchSm ? 'listWeek' : 'dayGridMonth');
  }, [matchSm]);

  const handleDatePrev = () => {
    const calendarEl = calendarRef.current;

    if (calendarEl) {
      const calendarApi = calendarEl.getApi();

      calendarApi.prev();
      setDate(calendarApi.getDate());
    }
  };

  const handleDateNext = () => {
    const calendarEl = calendarRef.current;

    if (calendarEl) {
      const calendarApi = calendarEl.getApi();

      calendarApi.next();
      setDate(calendarApi.getDate());
    }
  };

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedRange, setSelectedRange] = useState<DateRange | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<FormikValues | null>(null);

  // calendar event select/add/edit/delete
  const handleModalClose = () => {
    setIsModalOpen(false);
    setSelectedEvent(null);
    setSelectedRange(null);
  };

  const handleRangeSelect = (arg: DateSelectArg) => {
    const calendarEl = calendarRef.current;
    if (calendarEl) {
      const calendarApi = calendarEl.getApi();
      calendarApi.unselect();
    }

    setSelectedRange({
      start: arg.start,
      end: arg.end,
    });
    setIsModalOpen(true);
  };

  const handleEventSelect = (arg: EventClickArg) => {
    if (arg.event.id) {
      const selectEvent = events.find((_event: FormikValues) => _event.id === arg.event.id);
      setSelectedEvent(selectEvent as FormikValues[]);
    } else {
      setSelectedEvent(null);
    }
    setIsModalOpen(true);
  };

  // Enterprise Pattern: Update event with retry
  const { execute: updateEventOp } = useAsyncOperation(
    async (eventData: { id: string; allDay: boolean; start: Date | null; end: Date | null }) => {
      await calendarContext.updateEvent({
        eventId: eventData.id,
        update: {
          allDay: eventData.allDay,
          start: eventData.start,
          end: eventData.end,
        },
      } as any);
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to update event',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      },
    }
  );

  const handleEventUpdate = async ({ event }: EventResizeDoneArg | EventDropArg) => {
    await updateEventOp({
      id: event.id,
      allDay: event.allDay,
      start: event.start,
      end: event.end,
    });
  };

  // Enterprise Pattern: Create event with retry
  const { execute: createEventOp } = useAsyncOperation(
    async (data: FormikValues) => {
      await calendarContext.addEvent(data);
      notificationContext.showNotification({
        message: 'Event created successfully',
        variant: 'alert',
        alert: { color: 'success', variant: 'filled' },
        close: true,
      });
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to create event',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      },
    }
  );

  const handleEventCreate = async (data: FormikValues) => {
    await createEventOp(data);
    handleModalClose();
  };

  // Enterprise Pattern: Update event details with retry
  const { execute: updateEventDetailsOp } = useAsyncOperation(
    async (params: { eventId: string; update: FormikValues }) => {
      await calendarContext.updateEvent(params as any);
      notificationContext.showNotification({
        message: 'Event updated successfully',
        variant: 'alert',
        alert: { color: 'success', variant: 'filled' },
        close: true,
      });
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to update event',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      },
    }
  );

  const handleUpdateEvent = async (eventId: string, update: FormikValues) => {
    await updateEventDetailsOp({ eventId, update });
    handleModalClose();
  };

  // Enterprise Pattern: Delete event with retry
  const { execute: deleteEventOp } = useAsyncOperation(
    async (id: string) => {
      await calendarContext.removeEvent(id);
      notificationContext.showNotification({
        message: 'Event deleted successfully',
        variant: 'alert',
        alert: { color: 'success', variant: 'filled' },
        close: true,
      });
      return true;
    },
    {
      retryCount: 1,
      retryDelay: 300,
      onError: () => {
        notificationContext.showNotification({
          message: 'Failed to delete event',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      },
    }
  );

  const handleEventDelete = async (id: string) => {
    await deleteEventOp(id);
    handleModalClose();
  };

  const handleAddClick = () => {
    setIsModalOpen(true);
  };

  if (loading) return <Loader />;

  return (
    <MainCard
      title="Event Calendar"
      secondary={
        <Button color="secondary" variant="contained" onClick={handleAddClick}>
          <AddAlarmTwoToneIcon fontSize="small" sx={{ mr: 0.75 }} />
          Add New Event
        </Button>
      }
    >
      <CalendarStyled>
        <Toolbar
          date={date}
          view={view}
          onClickNext={handleDateNext}
          onClickPrev={handleDatePrev}
          onClickToday={handleDateToday}
          onChangeView={handleViewChange}
        />
        <SubCard>
          <FullCalendar
            weekends
            editable
            droppable
            selectable
            events={events}
            ref={calendarRef}
            rerenderDelay={10}
            initialDate={date}
            initialView={view}
            dayMaxEventRows={3}
            eventDisplay="block"
            headerToolbar={false}
            allDayMaintainDuration
            eventResizableFromStart
            select={handleRangeSelect}
            eventDrop={handleEventUpdate}
            eventClick={handleEventSelect}
            eventResize={handleEventUpdate}
            height={matchSm ? 'auto' : 720}
            plugins={[listPlugin, dayGridPlugin, timelinePlugin, timeGridPlugin, interactionPlugin]}
          />
        </SubCard>
      </CalendarStyled>

      {/* Dialog renders its body even if not open */}
      <Dialog
        maxWidth="sm"
        fullWidth
        onClose={handleModalClose}
        open={isModalOpen}
        sx={{ '& .MuiDialog-paper': { p: 0 } }}
      >
        {isModalOpen && (
          <AddEventForm
            event={selectedEvent}
            range={selectedRange}
            onCancel={handleModalClose}
            handleDelete={handleEventDelete}
            handleCreate={handleEventCreate}
            handleUpdate={handleUpdateEvent}
          />
        )}
      </Dialog>
    </MainCard>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(Calendar);
