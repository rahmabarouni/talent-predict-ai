  /**
 * @name CV Extractor Service
 * @description Extract text from PDF/TXT files directly in the browser
 * @location FrontEnd/src/app/core/services/cv-extractor.service.ts
 *
 * This service handles PDF and TXT file extraction client-side
 * using pdfjs-dist library for PDFs and FileReader for TXT files.
 */

import { Injectable } from '@angular/core';
import * as pdfjsLib from 'pdfjs-dist';

@Injectable({
  providedIn: 'root',
})
export class CvExtractorService {
  constructor() {
    // Keep worker and library versions aligned to avoid PDF.js version mismatch.
    const version = (pdfjsLib as any).version || '5.6.205';
    pdfjsLib.GlobalWorkerOptions.workerSrc =
      `https://cdn.jsdelivr.net/npm/pdfjs-dist@${version}/build/pdf.worker.min.mjs`;
  }

  /**
   * Extract text from a file (PDF or TXT)
   * @param file - File object from input
   * @returns Promise with extracted text
   */
  async extractFromFile(file: File): Promise<{
    text: string;
    fileName: string;
    fileType: string;
    fileSize: number;
    pages?: number;
  }> {
    if (!file) {
      throw new Error('No file provided');
    }

    const fileType = file.type;
    const fileName = file.name;
    const fileSize = file.size;

    // Route based on file type
    if (fileType === 'application/pdf' || fileName.endsWith('.pdf')) {
      return this.extractFromPdf(file, fileName, fileSize);
    } else if (fileType === 'text/plain' || fileName.endsWith('.txt')) {
      return this.extractFromText(file, fileName, fileSize);
    } else {
      throw new Error(`Unsupported file type: ${fileType}`);
    }
  }

  /**
   * Extract text from PDF using pdf.js
   */
  private async extractFromPdf(
    file: File,
    fileName: string,
    fileSize: number
  ): Promise<{
    text: string;
    fileName: string;
    fileType: string;
    fileSize: number;
    pages: number;
  }> {
    try {
      const arrayBuffer = await file.arrayBuffer();
      let pdf: any;

      try {
        pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;
      } catch (_workerError) {
        // Fallback for environments where worker loading fails.
        pdf = await (pdfjsLib as any).getDocument({ data: arrayBuffer, disableWorker: true }).promise;
      }

      let fullText = '';
      const numPages = pdf.numPages;

      // Extract text from each page
      for (let pageNum = 1; pageNum <= numPages; pageNum++) {
        const page = await pdf.getPage(pageNum);
        const textContent = await page.getTextContent();
        const pageText = textContent.items
          .map((item: any) => item.str)
          .join(' ');
        fullText += pageText + '\n';
      }

      return {
        text: fullText.trim(),
        fileName: fileName,
        fileType: 'application/pdf',
        fileSize: fileSize,
        pages: numPages,
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      throw new Error(`Failed to extract PDF: ${message}`);
    }
  }

  /**
   * Extract text from TXT file
   */
  private async extractFromText(
    file: File,
    fileName: string,
    fileSize: number
  ): Promise<{
    text: string;
    fileName: string;
    fileType: string;
    fileSize: number;
  }> {
    try {
      const text = await file.text();
      return {
        text: text.trim(),
        fileName: fileName,
        fileType: 'text/plain',
        fileSize: fileSize,
      };
    } catch (error) {
      throw new Error(`Failed to extract TXT: ${error}`);
    }
  }

  /**
   * Get file type label
   */
  getFileTypeLabel(fileType: string): string {
    if (fileType === 'application/pdf') return 'PDF';
    if (fileType === 'text/plain') return 'Text File';
    return 'Unknown';
  }

  /**
   * Format file size for display
   */
  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
  }
}
