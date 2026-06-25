$f = "C:\Projet\TalentPredict-wt-clean-merged\FrontEnd\src\app\modules\admin\components\campaign-manager\campaign-manager.component.html"
$lines = [System.IO.File]::ReadAllLines($f)

# Keep lines 0-445 (everything up to and including the closing blank line before TAB 4 comment)
$keep = $lines[0..445]

$newBlock = @'

  <!-- TAB 4 - Direct Message -->
  @if (activeTab() === 'direct') {
  <div class="direct-layout">

    <!-- LEFT: Employee List -->
    <div class="direct-picker-col">
      <div class="direct-col-header">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" /><circle cx="12" cy="7" r="4" /></svg>
        <span>Employes ({{ allUsers().length }})</span>
      </div>

      <div class="emp-search-wrap">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        <input class="emp-search-input" id="direct-user-search" [ngModel]="directSearch()" (ngModelChange)="directSearch.set($event)" placeholder="Filtrer par nom, email..." />
      </div>

      <div class="emp-list">
        @if (usersLoading()) {
          <div class="emp-loading">Chargement des employes...</div>
        }
        @for (user of filteredUsers(); track user.id) {
        <div class="emp-row" [class.emp-selected]="directSelectedUser()?.id === user.id" (click)="selectDirectUser(user)">
          <div class="emp-avatar">{{ getUserInitials(user) }}</div>
          <div class="emp-details">
            <span class="emp-name">{{ user.firstName }} {{ user.lastName }}</span>
            <span class="emp-email">{{ user.email }}</span>
            @if (user.department) {
              <span class="emp-dept">{{ user.department }}</span>
            }
          </div>
          <div class="emp-status">
            <span class="emp-role-badge" [class.emp-role-admin]="user.role === 'ADMIN'">{{ user.role }}</span>
            @if (directSelectedUser()?.id === user.id) {
              <svg class="emp-check" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
            }
          </div>
        </div>
        }
        @if (!usersLoading() && filteredUsers().length === 0) {
          <div class="emp-empty">Aucun employe trouve.</div>
        }
      </div>
    </div>

    <!-- RIGHT: Message Composer -->
    <div class="direct-composer-col">
      <div class="direct-col-header">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg>
        <span>
          Message direct
          @if (directSelectedUser()) {
            <span class="composer-target-badge">a {{ directSelectedUser()!.firstName }} {{ directSelectedUser()!.lastName }}</span>
          }
        </span>
      </div>

      @if (!directSelectedUser()) {
        <div class="composer-hint">
          <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
          <p>Selectionnez un employe dans la liste pour lui envoyer un message direct</p>
        </div>
      }

      <div class="compose-form" [class.compose-form-hidden]="!directSelectedUser()">
        <div class="form-group">
          <label class="form-label">Type</label>
          <div class="type-selector">
            <button class="type-btn" [class.type-active-info]="directForm.type === 'INFO'" (click)="directForm.type = 'INFO'">Info</button>
            <button class="type-btn" [class.type-active-success]="directForm.type === 'SUCCESS'" (click)="directForm.type = 'SUCCESS'">Succes</button>
            <button class="type-btn" [class.type-active-warning]="directForm.type === 'WARNING'" (click)="directForm.type = 'WARNING'">Alerte</button>
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
          <textarea id="direct-msg-body" class="form-input compose-textarea" [(ngModel)]="directForm.body" rows="5" placeholder="Redigez votre message..."></textarea>
        </div>

        <div class="form-group email-alert-row">
          <label class="toggle-label" for="direct-email-alert">
            <input type="checkbox" [(ngModel)]="directForm.emailAlert" id="direct-email-alert" />
            <span class="toggle-text">Envoyer aussi par email</span>
          </label>
        </div>

        <button class="btn btn-primary btn-send" id="direct-send-btn"
          [disabled]="directSending() || !directSelectedUser() || !directForm.title.trim() || !directForm.body.trim()"
          (click)="sendDirectMessage()">
          @if (directSending()) {
            <span class="spinner-sm"></span> Envoi en cours...
          } @else {
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
            @if (directSelectedUser()) { Envoyer a {{ directSelectedUser()!.firstName }} {{ directSelectedUser()!.lastName }} }
            @else { Selectionnez un employe }
          }
        </button>
      </div>

      @if (directSentLog().length > 0) {
      <div class="direct-sent-log">
        <h4 class="sent-log-title">Envoyes cette session ({{ directSentLog().length }})</h4>
        @for (entry of directSentLog(); track entry.sentAt) {
        <div class="sent-entry">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#22c55e" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
          <span class="sent-to">{{ entry.name }}</span>
          <span class="sent-title-preview">{{ entry.title }}</span>
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

$result = $keep + $newBlock.Split("`n")
[System.IO.File]::WriteAllLines($f, $result, [System.Text.Encoding]::UTF8)
Write-Host "OK $($result.Count)"
