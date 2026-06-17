/**
 * Background Ambient Music Manager for Web Client
 * Continuous soft music, NOT move-based SFX
 */
class BgMusicManager {
    constructor() {
        this.audio = null;
        this.isEnabled = localStorage.getItem('chess_bg_music') !== 'false';
        this.volume = parseFloat(localStorage.getItem('chess_music_volume')) || 0.25;
        this.currentTrack = null;
        this.fadeInterval = null;
    }

    /**
     * Play ambient track with smooth fade-in
     */
    play(trackPath, loop = true) {
        if (!this.isEnabled) return;
        if (this.currentTrack === trackPath && this.audio && !this.audio.paused) return;

        this.currentTrack = trackPath;

        // Fade out current
        if (this.audio) {
            this.fadeOut(1500, () => this.startTrack(trackPath, loop));
        } else {
            this.startTrack(trackPath, loop);
        }
    }

    startTrack(trackPath, loop) {
        try {
            this.audio = new Audio(trackPath);
            this.audio.loop = loop;
            this.audio.volume = 0;
            this.audio.play().catch(e => console.log('Audio play failed:', e));
            this.fadeIn();
        } catch (e) {
            console.error('Failed to start audio track:', e);
        }
    }

    fadeIn(duration = 1500) {
        if (!this.audio) return;
        const steps = 15;
        const stepTime = duration / steps;
        const volumeStep = this.volume / steps;
        let step = 0;

        if (this.fadeInterval) clearInterval(this.fadeInterval);

        this.fadeInterval = setInterval(() => {
            step++;
            if (this.audio) {
                this.audio.volume = Math.min(volumeStep * step, this.volume);
            }
            if (step >= steps) clearInterval(this.fadeInterval);
        }, stepTime);
    }

    fadeOut(duration = 1500, callback) {
        if (!this.audio) {
            if (callback) callback();
            return;
        }
        const steps = 15;
        const stepTime = duration / steps;
        const volumeStep = this.audio.volume / steps;
        let step = 0;

        if (this.fadeInterval) clearInterval(this.fadeInterval);

        this.fadeInterval = setInterval(() => {
            step++;
            if (this.audio) {
                this.audio.volume = Math.max(this.audio.volume - volumeStep, 0);
            }
            if (step >= steps) {
                clearInterval(this.fadeInterval);
                if (this.audio) {
                    try {
                        this.audio.pause();
                        this.audio.currentTime = 0;
                    } catch (e) {}
                }
                if (callback) callback();
            }
        }, stepTime);
    }

    pause() {
        if (this.audio) {
            this.fadeOut(1000, () => {
                if (this.audio) this.audio.pause();
            });
        }
    }

    resume() {
        if (this.isEnabled && this.audio && this.audio.paused) {
            this.audio.play().catch(e => console.log('Audio resume failed:', e));
            this.fadeIn();
        }
    }

    stop() {
        this.fadeOut(1000, () => {
            if (this.audio) {
                try {
                    this.audio.pause();
                    this.audio.currentTime = 0;
                } catch (e) {}
                this.audio = null;
                this.currentTrack = null;
            }
        });
    }

    setVolume(vol) {
        this.volume = Math.max(0, Math.min(1, vol));
        localStorage.setItem('chess_music_volume', this.volume);
        if (this.audio) this.audio.volume = this.volume;
    }

    setEnabled(enabled) {
        this.isEnabled = enabled;
        localStorage.setItem('chess_bg_music', enabled);
        if (!enabled) {
            this.stop();
        } else if (this.currentTrack) {
            this.play(this.currentTrack);
        }
    }
}

// Global instance
window.bgMusic = new BgMusicManager();
