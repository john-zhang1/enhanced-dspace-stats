import { AfterViewInit, Component, ElementRef, Input, OnInit, ViewChild, OnDestroy } from '@angular/core';
import { Point, UsageReport } from '../../core/statistics/models/usage-report.model';
import { Observable } from 'rxjs';
import { DSONameService } from '../../core/breadcrumbs/dso-name.service';
import { map } from 'rxjs/operators';
import { getRemoteDataPayload, getFinishedRemoteData, getFirstCompletedRemoteData } from '../../core/shared/operators';
import { DSpaceObjectDataService } from '../../core/data/dspace-object-data.service';
import { TranslateService } from '@ngx-translate/core';
import { isEmpty } from '../../shared/empty.util';
import { Chart, ChartConfiguration } from 'chart.js';
import  * as L from 'leaflet';
import 'leaflet.markercluster';
import { HttpClient } from '@angular/common/http';

// Import Angular core and Chart.js modules
import { Subscription } from 'rxjs';
import { DSpaceObject } from '../../core/shared/dspace-object.model';
import { ItemDataService } from '../../core/data/item-data.service';
import { Item } from '../../core/shared/item.model';
import { RemoteData } from 'src/app/core/data/remote-data';

export interface TableViewData {
  title: string;
  views: number;
}

/**
 * Component representing a statistics chart for a given usage reports.
 */
@Component({
  selector: 'ds-statistics-chart',
  templateUrl: './statistics-chart.component.html',
  styleUrls: ['./statistics-chart.component.scss']
})
export class StatisticsChartComponent implements OnInit, AfterViewInit, OnDestroy {

  /**
   * The usage reports to display a statistics chart for
   */
  @Input()
  reports: UsageReport[];

  @Input()
  scope: any;

  /**
   * Boolean indicating whether the usage reports has data
   */
  hasData: boolean;

  constructor(
    protected dsoService: DSpaceObjectDataService,
    protected nameService: DSONameService,
    private translateService: TranslateService,
    protected http: HttpClient,
    private itemService: ItemDataService,
  ) {

  }

  ngOnDestroy(): void {
    // Clean up: Destroy the chart instance and unsubscribe from data loading
    if (this.chartViewsPerMonthLine) {
      this.chartViewsPerMonthLine.destroy();
    }

    if (this.chartTotalViewsDoughnut) {
      this.chartTotalViewsDoughnut.destroy();
    }

    if (this.map) {
      this.map.remove();
    }

    if (this.dataSubscriptionCountryMap) {
      this.dataSubscriptionCountryMap.unsubscribe();
    }
  }

  ngOnInit() {
    this.loadReportsData();
    this.useCountrydata();
  }

  useCountrydata() {
    // Load GeoJSON data and apply it to the map
    this.http.get('assets/js/countries.geojson').subscribe((geoJsonData: any) => {
      this.createChoroplethLayer(geoJsonData);
      this.geoJsonData = geoJsonData;
    });
  }

  ngAfterViewInit(): void {
    this.initCountryCodeNames();
    this.initializeTotalViewsDoughnut();
    this.initializeViewsPerMonthLine();
    this.initMap();
  }

  private chartViewsPerMonthLine: Chart | null = null;
  public labelViewsPerMonthLine: string[] = []; // Array for chart label
  public viewsPerMonthPageviews: number[] = [];
  public viewsPerMonthDownloads: number[] = [];

  private initializeViewsPerMonthLine(): void {
    const chartConfig: ChartConfiguration = {
      type: 'line',
      data: {
        labels: this.labelViewsPerMonthLine,
        datasets: [{
          label: 'Pageviews',
          data: this.viewsPerMonthPageviews,
          tension: 0.1,
          backgroundColor: 'rgba(75, 192, 192, 0.2)',
          borderColor: 'rgba(75, 192, 192, 1)',
          borderWidth: 1
        },
        {
          label: 'Downloads',
          data: this.viewsPerMonthDownloads,
          tension: 0.1,
          backgroundColor: 'rgba(255, 99, 132, 0.2)',
          borderColor: 'rgba(255,99,132,1)',
          borderWidth: 1
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          title: {
            display: true,
            text: 'Pageviews and downloads over the past 6 months'
          }
        },
        scales: {
          y: {
            beginAtZero: true
          }
        }    
      }
    };

    // Set up the chart after data loading
    const ctx = document.getElementById('viewsPerMonthLine') as HTMLCanvasElement;
    if (ctx) {
      this.chartViewsPerMonthLine = new Chart(ctx, chartConfig);
    }
  }

  private chartTotalViewsDoughnut: Chart | null = null;
  public chartDataTotalViewsDoughnut: number[] = [];

  private initializeTotalViewsDoughnut(): void {
    const chartConfig: ChartConfiguration = {
      type: 'doughnut',
      data: {
        labels: ['Pageviews '+this.formatNumber(this.chartDataTotalViewsDoughnut[0]), 'Downloads '+this.formatNumber(this.chartDataTotalViewsDoughnut[1])],
        datasets: [{
          label: 'Aggregated Dataset',
          data: this.chartDataTotalViewsDoughnut,
          backgroundColor: [
            'rgba(13, 150, 142, 0.2)',  // color for pageviews
            'rgba(255, 99, 132, 0.2)'   // color for downloads
          ],
          borderColor: [
            'rgba(13, 150, 142, 1)',    // border color for pageviews
            'rgba(255, 99, 132, 1)'     // border color for downloads
          ],
          borderWidth: 1
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'bottom',
            labels: {
              padding: 20,
              usePointStyle: true,
              pointStyle: 'circle'
            }
          },
          title: {
            display: true,
            text: 'Total pageviews and downloads'
          }
        }
      }
    };

    // Initialize the chart on the canvas element
    const ctx3 = document.getElementById('totalViewsDoughnut') as HTMLCanvasElement;
    if (ctx3) {
      this.chartTotalViewsDoughnut = new Chart(ctx3, chartConfig);
    }
  }

  private loadReportsData() {
    this.reports.forEach((report) => {
      switch (report.reportType) {
        case 'TotalNumberOfPageviews':
          this.hasData = report.points.length > 0;
          if (this.hasData) {
            this.chartDataTotalViewsDoughnut.push(report.points[0].values['views']);
          }
          break;
        case 'TotalNumberOfDownloads':
          this.hasData = report.points.length > 0;
          if (this.hasData) {
            this.chartDataTotalViewsDoughnut.push(report.points[0].values['views']);
          }
          break;
        case 'TotalPageviewsPerMonth':
          this.hasData = report.points.length > 0;
          if (this.hasData) {
            report.points.forEach((point) => {
              this.labelViewsPerMonthLine.push(point.label);
              this.viewsPerMonthPageviews.push(point.values['views']);
            })
          }
          break;
        case 'TotalDownloadsPerMonth':
          this.hasData = report.points.length > 0;
          if (this.hasData) {
            report.points.forEach((point) => {
              this.viewsPerMonthDownloads.push(point.values['views']);
            })
          }
          break;
        case 'TopItemsPageviews':
          this.hasData = report.points.length > 0;
          if (this.hasData) {
            this.itempageviews = report.points;
          }
          break;
        case 'TopItemsDownloads':
          this.hasData = report.points.length > 0;
          if (this.hasData) {
            this.itemdownloads = report.points;
          }
          break;
      case 'TopCountriesDownloads':
          this.hasData = report.points.length > 0;
          if (this.hasData) {
            report.points.forEach((point) => {
              this.countryData[point.id] = point.values['views'];
              this.countryLabels[point.label] = point.values['views'];
            })
          }
          break;
      case 'TopCitiesDownloads':
          this.hasData = report.points.length > 0;
          if (this.hasData) {
            report.points.forEach((point) => {
              this.cityData[point.id] = point.values['views'];
            })
          }
          break;

        case 'TopCities':
          this.chartType = 'pie';
          break;
        default:
          this.chartType = 'line';
      }
    })
  }

  public itempageviews: Point[] = [];
  public itemdownloads: Point[] = [];

  getLabel(uuid: string): Observable<string> {
    return this.dsoService.findById(uuid).pipe(
      getFinishedRemoteData(),
      getRemoteDataPayload(),
      map((item) => !isEmpty(item) ? this.nameService.getName(item) : this.translateService.instant('statistics.chart.no-name')),
    );
  }

  getItem(uuid: string): Observable<DSpaceObject> {
    return this.itemService.findById(uuid).pipe(
      getFirstCompletedRemoteData(),
      map((rd: RemoteData<Item>) => {
        if (rd.hasSucceeded) {
          return rd.payload;
        }
        throw new Error(rd.errorMessage);
      })
    );
  }

  private geoJsonData: any;

  public chartType: ChartConfiguration['type'];

  public truncateString(str, maxLength) {
    if (str!==null && str.length > maxLength) {
      return str.slice(0, maxLength) + "...";
    } else {
      return str;
    }
  }

  // Map
  private map: any;
  private dataSubscriptionCountryMap: Subscription | null = null;

  // Sample population data for countries
  public countryData = {};
  public countryLabels = {};
  public countryCodeName = {};
  public countryData2 = {};

  public cityData = {};

  private initMap(): void {
    this.map = L.map('map', {
      center: [42.8, -10.5],
      zoom: 2
    });

    // Add a tile layer (OpenStreetMap)
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors'
    }).addTo(this.map);

    // Handle resize events
    window.addEventListener('resize', () => {
      this.map.invalidateSize();
    });
  }

  private createChoroplethLayer(geoJsonData: any): void {
    L.geoJSON(geoJsonData, {
      style: (feature) => this.style(feature),
      onEachFeature: (feature, layer) => this.onEachFeature(feature, layer)
    }).addTo(this.map);
  }

  private style(feature: any): any {
    const countryCode = feature.properties.ISO_A2;  // Match country code
    const viewCounts = this.countryData[countryCode];

    return {
      fillColor: this.getColor(viewCounts),
      weight: 2,
      opacity: 1,
      color: 'white',
      dashArray: '3',
      fillOpacity: 0.7
    };
  }

  private getColor(d: number): string {
    return d > 1000 ? '#800026' :
           d > 500  ? '#BD0026' :
           d > 200  ? '#E31A1C' :
           d > 100  ? '#FC4E2A' :
           d > 50   ? '#FD8D3C' :
           d > 20   ? '#FEB24C' :
           d > 10   ? '#FED976' : '#FFEDA0';
  }

  // Bind popups for each feature
  private onEachFeature(feature: any, layer: any): void {
    const countryCode = feature.properties.ISO_A2;
    const viewCounts = this.countryData[countryCode];
    layer.bindPopup(`<strong>${feature.properties.ADMIN}</strong><br>Downloads: ${this.formatNumber(viewCounts)}`);
    if(viewCounts > 0) {
      this.countryData2[feature.properties.ADMIN] = viewCounts;
    }
  }

  private async initCountryCodeNames(): Promise<void> {
    const dataFile = '/assets/js/countries.geojson';

    return new Promise((resolve) => {
      this.dataSubscriptionCountryMap = this.http.get<any>(dataFile)
        .subscribe(response => {
          if (response.features) {
            const countries = response.features;
            countries.forEach((country) => {
              this.countryCodeName[country.properties.ISO_A2] = country.properties.ADMIN;
            })
          }
          resolve();
        });
    });
  }

  public formatNumber(value: number | string, separator: string = ','): string {
    if (typeof value === 'string' && value.length === 0) {
      return "";
    }
    if (typeof value === 'undefined') {
      return "0";
    }
    
    const num = typeof value === 'string' ? parseFloat(value) : value;
    const isNegative = num < 0;
    const absNum = Math.abs(num);
    
    // Split number into integer and decimal parts
    const [integerPart, decimalPart = ''] = absNum.toString().split('.');
    
    // Add thousands separator to integer part
    const formattedInteger = integerPart
        .split('')
        .reverse()
        .reduce((acc, digit, index) => {
            const shouldAddSeparator = index > 0 && index % 3 === 0;
            return digit + (shouldAddSeparator ? separator : '') + acc;
        }, '');
    
    // Combine parts
    const result = formattedInteger + (decimalPart ? `.${decimalPart}` : '');

    // Add negative sign if needed
    return isNegative ? `-${result}` : result;
  }

  get countryTableData() {
    const entries =  Object.entries(this.countryLabels);
    const len = entries.length;
    const rows = [];

    for (let i = 0; i < entries.length; i += 2) {
      if(i+1 < len) {
        rows.push({
          country1: entries[i][0],
          downloads1: entries[i][1],
          country2: entries[i+1][0],
          downloads2: entries[i+1][1]
        });
      } else {
        let c1 = entries[i][0];
        let d1 = entries[i][1];
        const c2 = '';
        const d2 = '';
        rows.push({
          country1: entries[i][0],
          downloads1: entries[i][1],
          country2: '',
          downloads2: ''
        });
      }
    }
    return rows;
  }

  get cityTableData() {
    const entries =  Object.entries(this.cityData);
    const len = entries.length;
    const rows = [];

    for (let i = 0; i < entries.length; i += 2) {
      if(i+1 < len) {
        let c1 = entries[i][0];
        let d1 = entries[i][1];
        let c2 = entries[i+1][0];
        let d2 = entries[i+1][1];

        rows.push({
          city1: c1,
          downloads1: d1,
          city2: c2,
          downloads2: d2
        });  
      } else {
        let c1 = entries[i][0];
        let d1 = entries[i][1];
        const c2 = '';
        const d2 = '';
        rows.push({
          city1: c1,
          downloads1: d1,
          city2: c2,
          downloads2: d2
        });  
      }
    }
    return rows;
  }

}
