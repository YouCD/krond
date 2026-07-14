package main

import (
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

const (
	DefaultSocket     = "@krond"
	DefaultConfigFile = "/data/krond/krond.yaml"
	DefaultLogFile    = "/data/krond/krond.log"
	DefaultPidFile    = "/data/krond/krond.pid"
	DefaultLogTarget  = "both"
)

type Job struct {
	ID           int        `yaml:"id" json:"id"`
	Name         string     `yaml:"name" json:"name"`
	Schedule     string     `yaml:"schedule" json:"schedule"`
	Command      string     `yaml:"command" json:"command"`
	Enabled      bool       `yaml:"enabled" json:"enabled"`
	Next         *time.Time `yaml:"-" json:"next,omitempty"`
	LastRun      *time.Time `yaml:"-" json:"last_run,omitempty"`
	LastDuration string     `yaml:"-" json:"last_duration,omitempty"`
	LastExitCode *int       `yaml:"-" json:"last_exit_code,omitempty"`
}

type Config struct {
	Socket    string `yaml:"socket"`
	LogFile   string `yaml:"log_file"`
	PidFile   string `yaml:"pid_file"`
	LogTarget string `yaml:"log_target"`
	Jobs      []Job  `yaml:"jobs"`
}

func LoadConfig(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	cfg := &Config{}
	if err := yaml.Unmarshal(data, cfg); err != nil {
		return nil, err
	}
	if cfg.Socket == "" {
		cfg.Socket = DefaultSocket
	}
	if cfg.LogFile == "" {
		cfg.LogFile = DefaultLogFile
	}
	if cfg.PidFile == "" {
		cfg.PidFile = DefaultPidFile
	}
	if cfg.LogTarget == "" {
		cfg.LogTarget = DefaultLogTarget
	}
	return cfg, nil
}

func SaveConfig(cfg *Config, path string) error {
	data, err := yaml.Marshal(cfg)
	if err != nil {
		return err
	}
	return os.WriteFile(path, data, 0644)
}
