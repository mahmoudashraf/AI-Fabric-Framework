'use client';

// project import
import Error500 from 'views/maintenance/500';

// ==============================|| ERROR 500 - MAIN ||============================== //

// todo: testing of this page is pending. Need to figure out how to test this. Waiting to see
// if this comes automatically when some error appears

export default function GlobalError({
  error: _error,
  reset: _reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <html>
      <body>
        <Error500 />
      </body>
    </html>
  );
}
