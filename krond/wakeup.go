package main

import (
	"fmt"
	"time"

	"golang.org/x/sys/unix"
)

type WakeTimer struct {
	fd int
}

func NewWakeTimer() (*WakeTimer, error) {
	fd, err := unix.TimerfdCreate(unix.CLOCK_BOOTTIME_ALARM, 0)
	if err != nil {
		return nil, fmt.Errorf("timerfd_create(CLOCK_BOOTTIME_ALARM): %w", err)
	}
	return &WakeTimer{fd: fd}, nil
}

func (t *WakeTimer) Set(deadline time.Time) error {
	d := deadline.Round(0).Sub(time.Now().Round(0))
	if d < 0 {
		d = 0
	}
	spec := unix.ItimerSpec{}
	spec.Value.Sec = int64(d / time.Second)
	spec.Value.Nsec = int64(d % time.Second)

	return unix.TimerfdSettime(t.fd, 0, &spec, nil)
}

func (t *WakeTimer) Disarm() error {
	spec := unix.ItimerSpec{}
	return unix.TimerfdSettime(t.fd, 0, &spec, nil)
}

func (t *WakeTimer) Cancel() error {
	spec := unix.ItimerSpec{}
	spec.Value.Nsec = 1
	return unix.TimerfdSettime(t.fd, 0, &spec, nil)
}

func (t *WakeTimer) Fd() int {
	return t.fd
}

func (t *WakeTimer) Close() error {
	return unix.Close(t.fd)
}
