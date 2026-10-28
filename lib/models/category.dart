/*
 * FLauncher
 * Copyright (C) 2024 Oscar Rojas
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import 'dart:collection';

import 'app.dart';

enum CategorySort { manual, alphabetical, lastUsed }

enum CategorySource { custom, tvApps, androidApps }

class Category {
  static const int RowHeight = 110;
  static const CategorySort Sort = CategorySort.manual;

  final int id;
  int order;
  int rowHeight;
  String name;
  bool hidden;
  CategorySort sort;
  final CategorySource source;
  final List<App> applications;

  Category({
    required this.name,
    this.id = 0,
    this.order = 0,
    this.rowHeight = Category.RowHeight,
    this.sort = Category.Sort,
    this.source = CategorySource.custom,
    this.hidden = false,
  }) : applications = [];

  Category.withApplications({
    required this.name,
    required this.applications,
    this.id = 0,
    this.order = 0,
    this.rowHeight = Category.RowHeight,
    this.sort = Category.Sort,
    this.source = CategorySource.custom,
    this.hidden = false,
  });

  Category unmodifiable() => Category.withApplications(
    name: name,
    id: id,
    order: order,
    rowHeight: rowHeight,
    sort: sort,
    source: source,
    hidden: hidden,
    applications: UnmodifiableListView(applications),
  );
}
