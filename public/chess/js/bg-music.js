/**
 * Background Ambient Music Manager for Web Client
 * Robust single-pipeline state machine to prevent track and volume clashes.
 */
class BgMusicManager {
    constructor() {
        this.audio = null;
        this.isEnabled = localStorage.getItem('chess_bg_music') !== 'false';
        this.volume = parseFloat(localStorage.getItem('chess_music_volume')) || 0.50;
        
        this.currentTrack = null;
        this.targetTrack = null;
        this.targetLoop = undefined;
        
        this.currentVolume = 0;
        this.targetVolume = 0;
        
        this.zeroAction = null; // 'pause' or 'stop'
        this.fadeInterval = null;
    }

    startUpdateLoop() {
        if (this.fadeInterval) return;

        this.fadeInterval = setInterval(() => {
            let needsNextTick = false;

            // 1. If targetTrack has changed and audio is playing, force fade out first
            if (this.audio && this.targetTrack !== this.currentTrack && this.targetVolume !== 0) {
                this.targetVolume = 0;
                this.zeroAction = 'stop';
            }

            // 2. If no audio playing but we have a targetTrack, initialize it
            if (!this.audio && this.targetTrack) {
                this.startNewTrack(this.targetTrack, this.targetLoop);
                needsNextTick = true;
            } else if (this.audio) {
                // Adjust volume towards targetVolume
                const step = this.volume > 0 ? Math.max(this.volume / 40, 0.005) : 0.01;
                const diff = this.targetVolume - this.currentVolume;

                if (Math.abs(diff) < 0.001) {
                    this.currentVolume = this.targetVolume;
                    this.audio.volume = this.currentVolume;

                    if (this.currentVolume === 0) {
                        this.handleZeroVolumeReached();
                        if (this.targetTrack) {
                            needsNextTick = true;
                        }
                    }
                } else {
                    if (this.currentVolume < this.targetVolume) {
                        this.currentVolume = Math.min(this.currentVolume + step, this.targetVolume);
                    } else {
                        this.currentVolume = Math.max(this.currentVolume - step, this.targetVolume);
                    }
                    this.audio.volume = this.currentVolume;
                    needsNextTick = true;
                }
            }

            if (!needsNextTick) {
                clearInterval(this.fadeInterval);
                this.fadeInterval = null;
            }
        }, 50);
    }

    startNewTrack(trackPath, loop) {
        try {
            if (this.audio) {
                this.audio.pause();
                this.audio = null;
            }
            this.audio = new Audio(trackPath);
            
            // Auto-detect looping behavior if not explicitly specified
            if (typeof loop === 'boolean') {
                this.audio.loop = loop;
            } else {
                const isEndTrack = trackPath.includes('u_ng6e91f4wa-bg') || trackPath.includes('u_o0a9yfwhsr-aviator');
                this.audio.loop = !isEndTrack;
            }
            this.audio.volume = 0;
            
            this.currentTrack = trackPath;
            this.currentVolume = 0;
            this.targetVolume = this.volume;
            this.zeroAction = null;

            this.audio.play().catch(e => {
                console.log('Audio play blocked or failed:', e);
            });
        } catch (e) {
            console.error('Failed to start audio track:', e);
            this.audio = null;
            this.currentTrack = null;
            this.targetTrack = null;
        }
    }

    handleZeroVolumeReached() {
        if (this.zeroAction === 'pause') {
            if (this.audio) {
                try {
                    this.audio.pause();
                } catch (e) {}
            }
        } else if (this.zeroAction === 'stop') {
            if (this.audio) {
                try {
                    this.audio.pause();
                    this.audio.currentTime = 0;
                } catch (e) {}
                this.audio = null;
                this.currentTrack = null;
            }
        }
        this.zeroAction = null;
    }

    play(trackPath, loop) {
        if (!this.isEnabled) {
            this.targetTrack = trackPath;
            this.targetLoop = loop;
            return;
        }

        // Don't restart if already playing/targeting the same track
        if (this.currentTrack === trackPath && this.targetTrack === trackPath && this.audio && !this.audio.paused) {
            if (this.targetVolume === 0) {
                this.targetVolume = this.volume;
                this.startUpdateLoop();
            }
            return;
        }

        this.targetTrack = trackPath;
        this.targetLoop = loop;
        this.startUpdateLoop();
    }

    pause() {
        if (this.audio && !this.audio.paused) {
            this.targetVolume = 0;
            this.zeroAction = 'pause';
            this.startUpdateLoop();
        }
    }

    resume() {
        if (this.isEnabled && this.currentTrack) {
            if (this.audio) {
                try {
                    this.audio.play().catch(e => console.log('Audio resume failed:', e));
                } catch (e) {}
            }
            this.targetVolume = this.volume;
            this.zeroAction = null;
            this.startUpdateLoop();
        }
    }

    stop() {
        this.targetTrack = null;
        this.targetVolume = 0;
        this.zeroAction = 'stop';
        this.startUpdateLoop();
    }

    setVolume(vol) {
        this.volume = Math.max(0, Math.min(1, vol));
        localStorage.setItem('chess_music_volume', this.volume);
        
        // If playing/fading to active volume, adjust targetVolume and transition
        if (this.audio && this.targetVolume > 0) {
            this.targetVolume = this.volume;
            this.startUpdateLoop();
        }
    }

    setEnabled(enabled) {
        this.isEnabled = enabled;
        localStorage.setItem('chess_bg_music', enabled);
        if (!enabled) {
            this.stop();
        } else if (this.targetTrack) {
            this.play(this.targetTrack, this.targetLoop);
        }
    }
}

// Global instance
window.bgMusic = new BgMusicManager();
