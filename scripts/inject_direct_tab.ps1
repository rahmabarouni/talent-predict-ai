$filePath = "C:\Projet\TalentPredict-wt-clean-merged\FrontEnd\src\app\modules\admin\components\campaign-manager\campaign-manager.component.html"

$newBlock = @'

  <!-- TAB 4 - Direct Message -->
  @if (activeTab() === 'direct') {
  <div class="direct-layout">
    <div class="direct-picker-col">
      <div class="direct-col-header">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" /><circle cx="12" cy="7" r="4" /></svg>
        <span>Destinataire</span>
      </div>
      @if (directSelectedUser()) {
      <div class="selected-user-card animate-fade-in">
        <div class="suc-avatar">{{ getUserInitials(directSelectedUser()!) }}</div>
        <div class="suc-info">
          <p class="suc-name">{{ directSelectedUser()!.firstName }} {{ directSelectedUser()!.lastName }}</p>
          <p class="suc-email">{{ directSelectedUser()!.email }}</p>
          @if (directSelectedUser()!.department) { <span class="suc-dept">{{ directSelectedUser()!.department }}</span> }
        </div>
        <button class="suc-clear" (click)="clearDirectUser()"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg></button>
      </div>
      } @else {
      <div class="user-search-wrap">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" /></svg>
        <input class="form-input user-search-input" id="direct-user-search" [ngModel]="directSearch()" (ngModelChange)="directSearch.set($event)" placeholder="Rechercher par nom, email..." />
      </div>
      @if (usersLoading()) { <div class="direct-loading">Chargement...</div> }
      <div class="user-list">
        @for (user of filteredUsers(); track user.id) {
        <div class="user-list-item" (click)="selectDirectUser(user)">
          <div class="uli-avatar">{{ getUserInitials(user) }}</div>
          <div class="uli-info"><span class="uli-name">{{ user.firstName }} {{ user.lastName }}</span><span class="uli-email">{{ user.email }}</span></div>
          <div class="uli-role" [class.uli-admin]="user.role === 'ADMIN'">{{ user.role }}</div>
        </div>
        }
        @if (!usersLoading() && filteredUsers().length === 0) { <div class="direct-empty">Aucun utilisateur.</div> }
      </div>
      }
    </div>
    <div class="direct-composer-col">
      <div class="direct-col-header">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" /></svg>
        <span>Composer le message</span>
      </div>
      <div class="composer-panel" [class.composer-disabled]="!directSelectedUser()">
        @if (!directSelectedUser()) {
          <div class="composer-placeholder">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" /></svg>
            <p>Selectionnez un destinataire pour composer un message</p>
          </div>
        }
        @if (directSelectedUser()) {
        <div class="compose-form animate-fade-in">
          <div class="form-group">
            <label class="form-label">Type</label>
            <div class="type-selector">
              <button class="type-btn" [class.type-active-info]="directForm.type === 'INFO'" (click)="directForm.type = 'INFO'">Info</button>
              <button class="type-btn" [class.type-active-success]="directForm.type === 'SUCCESS'" (click)="directForm.type = 'SUCCESS'">Succes</button>
              <button class="type-btn" [class.type-active-warning]="directForm.type === 'WARNING'" (click)="directForm.type = 'WARNING'">Avert.</button>
              <button class="type-btn" [class.type-active-error]="directForm.type === 'ERROR'" (click)="directForm.type = 'ERROR'">Urgent</button>
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">Titre *</label>
            <input id="direct-msg-title" class="form-input" [(ngModel)]="directForm.title" placeholder="Ex: Votre evaluation est disponible" maxlength="180" />
            <span class="char-count">{{ directForm.title.length }}/180</span>
          </div>
          <div class="form-group">
            <label class="form-label">Message *</label>
            <textarea id="direct-msg-body" class="form-input compose-textarea" [(ngModel)]="directForm.body" rows="6" placeholder="Redigez votre message..."></textarea>
          </div>
          <div class="form-group email-alert-row">
            <label class="toggle-label" for="direct-email-alert">
              <input type="checkbox" [(ngModel)]="directForm.emailAlert" id="direct-email-alert" />
              <span class="toggle-text">Envoyer aussi par email</span>
            </label>
          </div>
          <button class="btn btn-primary btn-send" id="direct-send-btn" [disabled]="directSending() || !directForm.title.trim() || !directForm.body.trim()" (click)="sendDirectMessage()">
            @if (directSending()) { <span class="spinner-sm"></span> Envoi... }
            @else {
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="22" y1="2" x2="11" y2="13" /><polygon points="22 2 15 22 11 13 2 9 22 2" /></svg>
              Envoyer a {{ directSelectedUser()!.firstName }}
            }
          </button>
        </div>
        }
      </div>
      @if (directSentLog().length > 0) {
      <div class="direct-sent-log">
        <h4 class="sent-log-title">Session ({{ directSentLog().length }})</h4>
        @for (entry of directSentLog(); track entry.sentAt) {
        <div class="sent-entry">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#22c55e" stroke-width="2.5"><polyline points="20 6 9 17 4 12" /></svg>
          <span class="sent-to">{{ entry.name }}</span>
          <span class="sent-title-preview">{{ entry.title | slice:0:45 }}</span>
          <span class="sent-at">{{ entry.sentAt }}</span>
        </div>
        }
      </div>
      }
    </div>
  </div>
  }

</section>
'@

# Read original, strip the closing </section> line, then append new block
$original = [System.IO.File]::ReadAllText($filePath, [System.Text.Encoding]::UTF8)
$updated = $original -replace '(?s)</section>\s*$', $newBlock
[System.IO.File]::WriteAllText($filePath, $updated, [System.Text.Encoding]::UTF8)
Write-Host "SUCCESS: $(($updated -split '\n').Count) lines"
